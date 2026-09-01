package sqlsolver.superopt.liastar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import sqlsolver.common.utils.Printer;
import sqlsolver.superopt.logic.LogicSupport;

/**
 * Decides a linear LIA* formula with cvc5 instead of SQLSolver's own star solver.
 *
 * <p>SQLSolver's pipeline reaches this fragment in {@link LiaSolver#checkOverapp}: once
 * parameters have been pushed up and removed and multiplications have been abstracted into
 * fresh variables, what is left is a LIA* formula whose star bodies are linear -- exactly
 * what cvc5's {@code int.star-contains} accepts. SQLSolver would go on to eliminate the
 * stars itself, computing a semi-linear set per star ({@code LiaStar.expandStar} ->
 * {@link sqlsolver.superopt.liastar.transformer.LiaTransformer}) and handing the resulting
 * plain LIA formula to z3; that elimination falls back to an over-approximation whenever
 * the equivalent construction fails, so it can only ever answer <em>unsat</em>. This
 * backend instead hands the star formula to cvc5 as-is.
 *
 * <p>The formula is handed over as an SMT2 script produced by
 * {@link Cvc5LiaStarSolver#toSmt2Script} -- the same encoder that dumps the exported cvc5
 * benchmarks -- so this backend solves precisely the formula those benchmark files pose.
 *
 * <p>cvc5 runs as a child process rather than through its Java API, because only the child
 * can be bounded. cvc5's own {@code tlimit} is enforced by an alarm in its command-line
 * driver, not inside the library: set through the API it is accepted and reported back by
 * {@code getOption}, yet a hard star formula runs past it indefinitely. Java cannot
 * interrupt a native call and the API exposes no {@code interrupt}, so an in-process check
 * that overruns keeps a core busy for the rest of the run -- which corrupts not just timings
 * but results, since the contention pushes later benchmarks over their own deadline. A child
 * process honors {@code --tlimit} and can be killed outright.
 *
 * <p>Soundness of the answer: unsat always transfers back to the input formula. Sat transfers
 * only when the encoding and the steps that produced the formula preserve satisfiability
 * (see {@link LiaSolver} and the {@code satPreserving} parameter): int.star-contains takes a
 * closed lambda, so a leftover star parameter has to be bound per-summand, which weakens the
 * formula. A sat that cannot be transferred is reported as unknown.
 */
public final class Cvc5LiaStarBackend
{
  /**
   * Where to find the cvc5 executable. Unset falls back to the first existing candidate of
   * {@link #BINARY_CANDIDATES}, and then to {@code cvc5} on PATH.
   */
  public static final String PROPERTY_BINARY = "sqlsolver.liastar.cvc5.binary";

  /** Directory to save the scripts cvc5 does not answer, for offline analysis; unset = off. */
  public static final String PROPERTY_DUMP_FAILURES = "sqlsolver.liastar.cvc5.dumpFailures";
  private static final AtomicLong FAILURE_INDEX = new AtomicLong();

  /** The liastar cvc5 build, as laid out by the fmcad26 harness relative to the SQLSolver
   * root that the benchmark runner works in. */
  private static final String[] BINARY_CANDIDATES = {
      "../cvc5/build/install/bin/cvc5", "cvc5/build/install/bin/cvc5"};

  // Attribution of what happens to each LIA* query, written to cvc5_backend_stats.txt at
  // exit. Without it an "unknown" verdict is indistinguishable between cvc5 declining, cvc5
  // aborting, the query never being sent, and the answer being suppressed as untransferable.
  private static final java.util.concurrent.atomic.AtomicReference<String> LAST_FAILURE =
      new java.util.concurrent.atomic.AtomicReference<>();
  private static final AtomicLong SENT = new AtomicLong();
  private static final AtomicLong UNSAT_COUNT = new AtomicLong();
  private static final AtomicLong SAT_COUNT = new AtomicLong();
  private static final AtomicLong SAT_SUPPRESSED = new AtomicLong();
  private static final AtomicLong CVC5_UNKNOWN = new AtomicLong();
  private static final AtomicLong CVC5_FAILED = new AtomicLong();
  private static final AtomicLong OUT_OF_FRAGMENT = new AtomicLong();
  private static final AtomicLong UNDECIDED_FALLBACK = new AtomicLong();
  // Validation of the fragment test: how often nesting had to be reduced, and how many
  // formulas the whitelist would have rejected had it been applied before that reduction.
  private static final AtomicLong NESTING_REDUCED = new AtomicLong();
  private static final AtomicLong OUT_OF_FRAGMENT_BEFORE_REDUCTION = new AtomicLong();

  static
  {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try
      {
        Files.writeString(Paths.get("cvc5_backend_stats.txt"),
            String.format("sent=%d unsat=%d sat=%d satSuppressed=%d cvc5Unknown=%d "
                + "cvc5Failed=%d outOfFragment=%d undecidedFallback=%d%n",
                SENT.get(), UNSAT_COUNT.get(), SAT_COUNT.get(), SAT_SUPPRESSED.get(),
                CVC5_UNKNOWN.get(), CVC5_FAILED.get(), OUT_OF_FRAGMENT.get(),
                UNDECIDED_FALLBACK.get())
            + String.format("nestingReduced=%d outOfFragmentBeforeReduction=%d%n",
                NESTING_REDUCED.get(), OUT_OF_FRAGMENT_BEFORE_REDUCTION.get())
            + (LAST_FAILURE.get() == null ? "" : "\nfirst failure:\n" + LAST_FAILURE.get()));
      }
      catch (IOException ignored)
      {
      }
    }));
  }

  /** Records a formula that {@link LiaSolver} did not send because cvc5 cannot take it. */
  public static void countOutOfFragment()
  {
    OUT_OF_FRAGMENT.incrementAndGet();
  }

  /** Records a formula cvc5 was asked about but could not decide, left to SQLSolver. */
  public static void countUndecided()
  {
    UNDECIDED_FALLBACK.incrementAndGet();
  }

  /** Records that a formula needed its nested stars eliminated before cvc5 could see it. */
  public static void countNestingReduced()
  {
    NESTING_REDUCED.incrementAndGet();
  }

  /** Records a formula the fragment test would have rejected before nesting was reduced. */
  public static void countOutOfFragmentBeforeReduction()
  {
    OUT_OF_FRAGMENT_BEFORE_REDUCTION.incrementAndGet();
  }

  private Cvc5LiaStarBackend() {}

  /**
   * Checks satisfiability of the linear LIA* formula {@code f} with cvc5.
   *
   * @param f a LIA* formula whose star bodies are linear (parameters removed, multiplication
   *     abstracted); stars are translated to {@code int.star-contains}
   * @param timeoutMillis wall-clock budget for the cvc5 child, passed as its {@code --tlimit}
   *     and enforced a second time by killing the child; non-positive means no limit
   * @param satPreserving whether the caller reached {@code f} by steps that preserve
   *     satisfiability, so that a model of {@code f} is a model of what the caller started
   *     from; when false a sat verdict is downgraded to unknown
   * @return unsat/sat as decided by cvc5, or unknown if cvc5 cannot decide, if sat cannot be
   *     transferred back, or if the translation or the solver call fails
   */
  public static LiaSolverStatus solve(LiaStar f, long timeoutMillis, boolean satPreserving)
  {
    // A leftover star parameter cannot be encoded exactly (see the class comment);
    // the encoding is then a weakening, from which only unsat transfers back.
    final Set<String> starParams = Cvc5LiaStarSolver.collectStarParams(f);
    final boolean exact = satPreserving && starParams.isEmpty();

    final String script;
    try
    {
      script = Cvc5LiaStarSolver.toSmt2Script(f);
    }
    catch (UnsupportedOperationException e)
    {
      // the formula uses a construct the cvc5 encoding does not cover
      if (LogicSupport.dumpLiaFormulas)
        Printer.output.println("cvc5 backend: unsupported construct: " + e.getMessage());
      return LiaSolverStatus.UNKNOWN;
    }
    if (LogicSupport.dumpLiaFormulas)
    {
      Printer.output.println("cvc5 backend: solving"
          + (exact ? "" : " (weakened: star parameters " + starParams + ")") + ":");
      Printer.output.println(script);
    }

    SENT.incrementAndGet();
    final String verdict = runCvc5(script, timeoutMillis);
    if ("unsat".equals(verdict))
    {
      UNSAT_COUNT.incrementAndGet();
      return LiaSolverStatus.UNSAT;
    }
    if ("sat".equals(verdict))
    {
      if (exact)
      {
        SAT_COUNT.incrementAndGet();
        return LiaSolverStatus.SAT;
      }
      SAT_SUPPRESSED.incrementAndGet();
      return LiaSolverStatus.UNKNOWN;
    }
    if ("unknown".equals(verdict))
      CVC5_UNKNOWN.incrementAndGet();
    else
      CVC5_FAILED.incrementAndGet();
    return LiaSolverStatus.UNKNOWN;
  }

  /**
   * Runs cvc5 on {@code script} in a child process and returns its verdict as
   * {@code sat}/{@code unsat}, or null for anything else (unknown, a resource-out, a crash, a
   * kill). The child is bounded twice over: by {@code --tlimit}, which its driver enforces,
   * and by killing it a second past that, so that a build ignoring the limit cannot outlive
   * its benchmark.
   */
  private static String runCvc5(String script, long timeoutMillis)
  {
    Path scriptFile = null;
    Process process = null;
    try
    {
      scriptFile = Files.createTempFile("liastar", ".smt2");
      Files.writeString(scriptFile, script, StandardCharsets.UTF_8);

      final List<String> command = new ArrayList<>();
      command.add(binary());
      if (timeoutMillis > 0)
        command.add("--tlimit=" + timeoutMillis);
      command.add(scriptFile.toString());

      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      final String output = readAll(process.getInputStream());
      if (timeoutMillis > 0)
      {
        if (!process.waitFor(timeoutMillis + 1000, TimeUnit.MILLISECONDS))
        {
          process.destroyForcibly();
          return null;
        }
      }
      else
      {
        process.waitFor();
      }

      // the last non-empty line is the check-sat answer; earlier lines may carry warnings
      String verdict = null;
      for (String line : output.split("\\R"))
      {
        final String trimmed = line.trim();
        if (trimmed.equals("sat") || trimmed.equals("unsat") || trimmed.equals("unknown"))
          verdict = trimmed;
      }
      if (verdict == null)
      {
        if (LAST_FAILURE.get() == null && !output.isBlank())
          LAST_FAILURE.set(output.strip());
        saveFailure(script, output);
      }
      if (LogicSupport.dumpLiaFormulas && verdict == null)
        Printer.output.println("cvc5 backend: no verdict from cvc5: " + output.trim());
      return verdict;
    }
    catch (InterruptedException e)
    {
      // the caller's watchdog gave up on this benchmark: take the child down with us
      Thread.currentThread().interrupt();
      return null;
    }
    catch (IOException | RuntimeException e)
    {
      if (LogicSupport.dumpLiaFormulas)
        Printer.output.println("cvc5 backend: " + e);
      return null;
    }
    finally
    {
      if (process != null && process.isAlive())
        process.destroyForcibly();
      if (scriptFile != null)
      {
        try
        {
          Files.deleteIfExists(scriptFile);
        }
        catch (IOException ignored)
        {
        }
      }
    }
  }

  /** Saves a script cvc5 gave no verdict on, when {@link #PROPERTY_DUMP_FAILURES} names a
   * directory, so the query can be replayed offline (e.g. under a longer time limit). */
  private static void saveFailure(String script, String output)
  {
    final String dir = System.getProperty(PROPERTY_DUMP_FAILURES);
    if (dir == null || dir.isBlank())
      return;
    try
    {
      final Path target = Paths.get(dir);
      Files.createDirectories(target);
      final long index = FAILURE_INDEX.getAndIncrement();
      Files.writeString(target.resolve(String.format("failure-%04d.smt2", index)),
          "; cvc5 said: " + output.strip().replace("\n", " ") + "\n" + script);
    }
    catch (IOException | RuntimeException ignored)
    {
    }
  }

  /** The cvc5 executable to run; see {@link #PROPERTY_BINARY}. */
  private static String binary()
  {
    final String configured = System.getProperty(PROPERTY_BINARY);
    if (configured != null && !configured.isBlank())
      return configured;
    for (String candidate : BINARY_CANDIDATES)
    {
      if (Files.isExecutable(Paths.get(candidate)))
        return candidate;
    }
    return "cvc5";
  }

  private static String readAll(InputStream in) throws IOException
  {
    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
  }
}
