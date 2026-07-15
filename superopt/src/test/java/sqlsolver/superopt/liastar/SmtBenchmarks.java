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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import io.github.cvc5.Context;
import io.github.cvc5.Kind;
import io.github.cvc5.Solver;
import io.github.cvc5.Sort;
import io.github.cvc5.Term;
import io.github.cvc5.TermManager;

public class SmtBenchmarks
{
  private static final Properties[] LIA_SOLVER_CONFIGS;

  static
  {
    LIA_SOLVER_CONFIGS = new Properties[2];
    LIA_SOLVER_CONFIGS[0] = new Properties();
    LIA_SOLVER_CONFIGS[0].setProperty(
        LiaSolver.CONFIG_KEY_PARAM_REMOVAL_MODE, LiaSolver.CONFIG_VALUE_PARAM_REMOVAL_MODE_INWARD);
    LIA_SOLVER_CONFIGS[1] = new Properties();
    LIA_SOLVER_CONFIGS[1].setProperty(
        LiaSolver.CONFIG_KEY_PARAM_REMOVAL_MODE, LiaSolver.CONFIG_VALUE_PARAM_REMOVAL_MODE_OUTWARD);
  }

  @Test
  public void cvc5Works()
  {
    TermManager tm = new TermManager();
    Solver slv = new Solver(tm);
    {
      slv.setLogic("ALL"); // Set the logic

      // Prove that if given x (Integer) and y (Real) then
      // the maximum value of y - x is 2/3

      // Sorts
      Sort real = tm.getRealSort();
      Sort integer = tm.getIntegerSort();

      // Variables
      Term x = tm.mkConst(integer, "x");
      Term y = tm.mkConst(real, "y");

      // Constants
      Term three = tm.mkInteger(3);
      Term neg2 = tm.mkInteger(-2);
      Term two_thirds = tm.mkReal(2, 3);

      // Terms
      Term three_y = tm.mkTerm(Kind.MULT, three, y);
      Term diff = tm.mkTerm(Kind.SUB, y, x);

      // Formulas
      Term x_geq_3y = tm.mkTerm(Kind.GEQ, x, three_y);
      Term x_leq_y = tm.mkTerm(Kind.LEQ, x, y);
      Term neg2_lt_x = tm.mkTerm(Kind.LT, neg2, x);

      Term assertions = tm.mkTerm(Kind.AND, x_geq_3y, x_leq_y, neg2_lt_x);

      System.out.println("Given the assertions " + assertions);
      slv.assertFormula(assertions);

      slv.push();
      Term diff_leq_two_thirds = tm.mkTerm(Kind.LEQ, diff, two_thirds);
      System.out.println("Prove that " + diff_leq_two_thirds + " with cvc5.");
      System.out.println("cvc5 should report UNSAT.");
      System.out.println(
          "Result from cvc5 is: " + slv.checkSatAssuming(diff_leq_two_thirds.notTerm()));
      slv.pop();

      System.out.println();

      slv.push();
      Term diff_is_two_thirds = tm.mkTerm(Kind.EQUAL, diff, two_thirds);
      slv.assertFormula(diff_is_two_thirds);
      System.out.println("Show that the assertions are consistent with ");
      System.out.println(diff_is_two_thirds + " with cvc5.");
      System.out.println("cvc5 should report SAT.");
      System.out.println("Result from cvc5 is: " + slv.checkSat());
      slv.pop();

      System.out.println("Thus the maximum value of (y - x) is 2/3.");
    }
    Context.deletePointers();
  }

  private LiaSolverStatus runSingleBenchmark(String filename)
  {
    SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
    LiaStar formula = smtToSqlSolver.translateFile(filename);
    System.out.println("formula:\n" + formula);
    var result = LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
    return result;
  }

  @Test
  public void runSingleBenchmarkQuery151Call0()
  {
    String filename = "cvc5/linear/calcite-query151-call-0.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
  }

  @Test
  public void runSingleBenchmarkMapaFol0000001()
  {
    String filename = "cvc5/sls-reachability/arith/cvc5_mapa/fol_0000001.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
  }

  @Test
  public void runBenchmarkMapaFol0000110()
  {
    String filename = "cvc5/sls-reachability/arith/cvc5_mapa/fol_0000110.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000055()
  {
    String filename = "cvc5/sls-reachability/card/cvc5_bapa/fol_0000055.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000055WithModel()
  {
    String filename = "cvc5/sls-reachability/soundness/fol_0000055.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000078()
  {
    String filename = "cvc5/sls-reachability/card/cvc5_bapa/fol_0000078.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000078WithModel()
  {
    String filename = "cvc5/sls-reachability/soundness/fol_0000078.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000116()
  {
    String filename = "cvc5/sls-reachability/card/cvc5_bapa/fol_0000116.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000116WithModel()
  {
    String filename = "cvc5/sls-reachability/soundness/fol_0000116.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000120()
  {
    String filename = "cvc5/sls-reachability/card/cvc5_bapa/fol_0000120.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000120WithModel()
  {
    String filename = "/home/mudathir/all/SQLSolver/cvc5/sls-reachability/fol_0000120.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkBapaFol0000113WithModel()
  {
    String filename = "cvc5/sls-reachability/card/cvc5_bapa/fol_0000113.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  /**
   * spark pair #90 (left-join filter pushdown) is truly equivalent, so this counterexample
   * formula is UNSAT. The dump is faithful only since Cvc5LiaStarSolver.translate started
   * eliminating star parameters exactly before export; the earlier dump bound the shared
   * parameters u21/u22 per-summand in the lambda, weakening the formula to SAT.
   */
  @Test
  public void runBenchmarkBapaQuery090WithModel()
  {
    String filename = "cvc5/linear/spark-query090-call-2.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runBenchmarkQuery048call0()
  {
    String filename = "cvc5/spark/query048-call-0.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runAllMapaBenchmarks()
  {
    String[] directories = {
        "cvc5/sls-reachability/arith/cvc5_mapa", "cvc5/sls-reachability/card/cvc5_mapa"};
    long timeoutSeconds = 100;
    String outputCsv = "sql_mapa.csv";

    runMultipleBenchmarks(directories, outputCsv, timeoutSeconds);
  }

  @Test
  public void runAllSqlSolverBenchmarks()
  {
    String[] directories = {"cvc5/linear"};
    long timeoutSeconds = 100;
    String outputCsv = "sql_solver.csv";

    runMultipleBenchmarks(directories, outputCsv, timeoutSeconds);
  }

  private void runMultipleBenchmarks(String[] directories, String outputCsv, long timeoutSeconds)
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

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(outputCsv))))
    {
      writer.println("filename,result,duration");
      writer.flush();

      for (Path file : files)
      {
        String filename = file.getFileName().toString();
        String result;
        double duration;
        long startNs = System.nanoTime();
        Future<LiaSolverStatus> future = executor.submit(() -> {
          SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
          LiaStar formula = smtToSqlSolver.translateFile(file.toString());
          return LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
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

        System.out.printf("%s,%s,%.3f%n", file, result, duration);
        writer.printf("%s,%s,%.3f%n", file, result, duration);
        writer.flush();
      }
    }
    catch (IOException e)
    {
      throw new RuntimeException("Failed to write CSV: " + outputCsv, e);
    }
    finally
    {
      executor.shutdownNow();
    }
  }

  @Test
  public void runAllBapaBenchmarks()
  {
    String[] directories = {
        "cvc5/sls-reachability/arith/cvc5_bapa", "cvc5/sls-reachability/card/cvc5_bapa"};
    long timeoutSeconds = 100;
    String outputCsv = "sql_bapa.csv";

    runMultipleBenchmarks(directories, outputCsv, timeoutSeconds);
  }

  @Test
  public void runSqlBenchmarks()
  {
    String[] directories = {""};
    long timeoutSeconds = 100;
    String outputCsv = "sql.csv";

    runMultipleBenchmarks(directories, outputCsv, timeoutSeconds);
  }

  @Test
  public void runBenchmarPaper2008()
  {
    String filename = "cvc5/paper2008.smt2";
    LiaSolverStatus result = runSingleBenchmark(filename);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  /**
   * Diagnoses why each benchmark under the {@code unsupported/} directory fails in the
   * SQLSolver pipeline. Records, per file, the phase that threw (translate vs. solve), the
   * exception class, and — for {@code UnsupportedOperationException} — the offending cvc5 Kind.
   * Output: unsupported_diagnosis.csv
   */
  @Test
  public void diagnoseUnsupported() throws IOException
  {
    Path root = Paths.get("unsupported");
    List<Path> files = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(root))
    {
      stream.filter(p -> p.toString().endsWith(".smt2"))
          .sorted(Comparator.naturalOrder())
          .forEach(files::add);
    }

    try (PrintWriter writer =
             new PrintWriter(Files.newBufferedWriter(Paths.get("unsupported_diagnosis.csv"))))
    {
      writer.println("filename,phase,exception,kind,detail");
      writer.flush();
      for (Path file : files)
      {
        String phase = "translate";
        String exception = "";
        String kind = "";
        String detail = "";
        try
        {
          SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
          LiaStar formula = smtToSqlSolver.translateFile(file.toString());
          phase = "solve";
          LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
          phase = "ok";
        }
        catch (Throwable e)
        {
          exception = e.getClass().getName();
          String msg = e.getMessage() == null ? "" : e.getMessage();
          String marker = "Unsupported Kind: ";
          if (msg.contains(marker))
          {
            String after = msg.substring(msg.indexOf(marker) + marker.length());
            int idx = after.indexOf(" in term:");
            kind = idx >= 0 ? after.substring(0, idx) : after;
            detail = idx >= 0 ? after.substring(idx + " in term:".length()).trim() : "";
          }
          else
          {
            kind = msg;
          }
        }
        kind = kind.replace(",", " ").replace("\n", " ").replace("\r", " ").trim();
        detail = detail.replace(",", " ").replace("\n", " ").replace("\r", " ").trim();
        if (detail.length() > 100)
          detail = detail.substring(0, 100);
        String line = String.format("%s,%s,%s,%s,%s", file, phase, exception, kind, detail);
        System.out.println("DIAG " + line);
        writer.println(line);
        writer.flush();
      }
    }
  }

  /**
   * Diagnoses a soundness discrepancy: files for which the original SQLSolver pipeline
   * returns UNSAT while cvc5 (liastar) and sls-reachability independently return SAT.
   * Runs the under- and over-approximation checks separately to localize the wrong UNSAT.
   */
  @Test
  public void diagnoseDiscrepancy() throws Exception
  {
    String[] files = {
        "cvc5/calcite/query026-call-4.smt2",
    };
    for (String filename : files)
    {
      System.out.println("\n################ DISCREPANCY: " + filename + " ################");
      SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
      LiaStar formula = smtToSqlSolver.translateFile(filename);
      System.out.println(">>> LiaStar formula:\n" + formula);
      System.out.println(">>> embeddingLayers = " + formula.embeddingLayers());

      LiaSolver solver = new LiaSolver(LIA_SOLVER_CONFIGS[1], formula); // OUTWARD
      String under;
      try
      {
        under = solver.checkUnderapp();
      }
      catch (Throwable t)
      {
        under = "THREW: " + t;
      }
      System.out.println(">>> checkUnderapp() = " + under);

      String over;
      try
      {
        over = solver.checkOverapp();
      }
      catch (Throwable t)
      {
        over = "THREW: " + t;
      }
      System.out.println(">>> checkOverapp()  = " + over);

      LiaSolverStatus status = LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
      System.out.println(">>> solve() = " + status);
      System.out.println("################ END " + filename + " ################");
    }
  }
}
