package sqlsolver.superopt.liastar;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Command-line runner for the benchmark suites of {@link SmtBenchmarks}, with a configurable
 * per-benchmark timeout and degree of parallelism (the JUnit tests hardcode 100 seconds).
 *
 * <pre>
 *   SmtBenchmarksMain &lt;bapa|mapa|sql&gt; [--timeout=SECONDS] [--jobs=N]
 *                     [--output=FILE] [--benchmarks=DIR]
 * </pre>
 *
 * Defaults: timeout 100 seconds; jobs = all processors but two for bapa/mapa, and 1 — i.e.
 * sequential, for accurate timings on its fast benchmarks — for sql; benchmarks directory
 * {@code ../benchmarks} (the fmcad26 layout, relative to the SQLSolver root that the gradle
 * task runs in); output {@code sql_bapa.csv} / {@code sql_mapa.csv} / {@code sql_solver.csv}
 * in the SQLSolver root, matching the JUnit tests.
 *
 * <p>Via gradle (which supplies java.library.path and the working directory):
 *
 * <pre>
 *   ./gradlew :superopt:smtBenchmarks -PbenchArgs="bapa --timeout=60 --jobs=8"
 * </pre>
 */
public class SmtBenchmarksMain
{
  private static final Properties LIA_SOLVER_CONFIG;

  static
  {
    LIA_SOLVER_CONFIG = new Properties();
    LIA_SOLVER_CONFIG.setProperty(
        LiaSolver.CONFIG_KEY_PARAM_REMOVAL_MODE, LiaSolver.CONFIG_VALUE_PARAM_REMOVAL_MODE_OUTWARD);
  }

  public static void main(String[] args)
  {
    String suite = null;
    long timeoutSeconds = 100;
    int jobs = -1; // -1: use the suite default
    String output = null;
    String benchmarksDir = "../benchmarks";

    for (String arg : args)
    {
      if (arg.equals("bapa") || arg.equals("mapa") || arg.equals("sql"))
        suite = arg;
      else if (arg.startsWith("--timeout="))
        timeoutSeconds = Long.parseLong(arg.substring("--timeout=".length()));
      else if (arg.startsWith("--jobs="))
        jobs = Integer.parseInt(arg.substring("--jobs=".length()));
      else if (arg.startsWith("--output="))
        output = arg.substring("--output=".length());
      else if (arg.startsWith("--benchmarks="))
        benchmarksDir = arg.substring("--benchmarks=".length());
      else
        usage("unknown argument: " + arg);
    }
    if (suite == null)
      usage("no suite given");
    if (timeoutSeconds <= 0)
      usage("--timeout must be positive");

    String[] directories;
    switch (suite)
    {
      case "bapa":
        directories = new String[] {
            benchmarksDir + "/arith/cvc5_bapa", benchmarksDir + "/card/cvc5_bapa"};
        if (output == null) output = "sql_bapa.csv";
        break;
      case "mapa":
        directories = new String[] {
            benchmarksDir + "/arith/cvc5_mapa", benchmarksDir + "/card/cvc5_mapa"};
        if (output == null) output = "sql_mapa.csv";
        break;
      default: // sql
        directories = new String[] {benchmarksDir + "/sql/linear"};
        if (output == null) output = "sql_solver.csv";
        break;
    }

    // sql runs on a single thread by default: its benchmarks are fast and
    // sequential execution gives accurate timings
    if (jobs <= 0)
      jobs = suite.equals("sql")
          ? 1
          : Math.max(1, Runtime.getRuntime().availableProcessors() - 2);

    System.out.printf("suite=%s timeout=%ds jobs=%d benchmarks=%s output=%s%n",
        suite, timeoutSeconds, jobs, benchmarksDir, output);
    runMultipleBenchmarks(directories, output, timeoutSeconds, jobs);
  }

  private static void usage(String message)
  {
    System.err.println("error: " + message);
    System.err.println(
        "usage: SmtBenchmarksMain <bapa|mapa|sql> [--timeout=SECONDS] [--jobs=N]");
    System.err.println(
        "                         [--output=FILE] [--benchmarks=DIR]");
    System.err.println(
        "defaults: --timeout=100; --jobs = processors-2 (bapa/mapa) or 1 (sql);");
    System.err.println(
        "          --benchmarks=../benchmarks; --output = sql_<suite>.csv");
    System.exit(2);
  }

  /**
   * Runs every smt2 file in the given directories through the SQLSolver pipeline, at most
   * {@code jobs} benchmarks concurrently, and writes filename,result,duration rows to
   * {@code outputCsv} as benchmarks finish (rows are in completion order when jobs > 1).
   * Each benchmark keeps the sequential version's timeout semantics: it runs on its own
   * dedicated worker thread, so the {@code timeoutSeconds} budget never counts time spent
   * waiting in the pool queue.
   */
  private static void runMultipleBenchmarks(
      String[] directories, String outputCsv, long timeoutSeconds, int jobs)
      throws RuntimeException
  {
    List<Path> files = new ArrayList<>();
    for (String dir : directories)
    {
      try (Stream<Path> stream = Files.list(Paths.get(dir)))
      {
        stream.filter(p -> p.toString().endsWith(".smt2"))
            .sorted(Comparator.naturalOrder())
            .forEach(files::add);
      }
      catch (IOException e)
      {
        throw new RuntimeException("Failed to list directory: " + dir, e);
      }
    }

    ExecutorService pool = Executors.newFixedThreadPool(jobs);
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(outputCsv))))
    {
      writer.println("filename,result,duration");
      writer.flush();

      List<Future<?>> pending = new ArrayList<>();
      for (Path file : files)
      {
        pending.add(pool.submit(() -> {
          String result;
          double duration;
          ExecutorService executor = Executors.newSingleThreadExecutor();
          long startNs = System.nanoTime();
          Future<LiaSolverStatus> future = executor.submit(() -> {
            SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
            LiaStar formula = smtToSqlSolver.translateFile(file.toString());
            return LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIG);
          });
          try
          {
            LiaSolverStatus status = future.get(timeoutSeconds, TimeUnit.SECONDS);
            result = status.toString();
            duration = (System.nanoTime() - startNs) / 1e9;
          }
          catch (TimeoutException e)
          {
            future.cancel(true);
            result = "timeout";
            duration = timeoutSeconds;
          }
          catch (Exception e)
          {
            System.out.println(e);
            result = "error";
            duration = (System.nanoTime() - startNs) / 1e9;
          }
          finally
          {
            executor.shutdownNow();
          }
          synchronized (writer)
          {
            System.out.printf("%s,%s,%.3f%n", file, result, duration);
            writer.printf("%s,%s,%.3f%n", file, result, duration);
            writer.flush();
          }
        }));
      }
      for (Future<?> task : pending)
      {
        try
        {
          task.get();
        }
        catch (Exception e)
        {
          System.out.println(e);
        }
      }
    }
    catch (IOException e)
    {
      throw new RuntimeException("Failed to write CSV: " + outputCsv, e);
    }
    finally
    {
      pool.shutdownNow();
    }
  }
}
