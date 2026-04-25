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

  @Test
  public void runSingleBenchmark()
  {
    String filename =
        "/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_mapa/fol_0000001.smt2";
    SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
    LiaStar formula = smtToSqlSolver.translateFile(filename);
    System.out.println("formula:\n" + formula);

    var result = LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
    System.out.println("result: " + result);
  }

  @Test
  public void runBenchmarkFol0000110()
  {
    String filename =
        "/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_mapa/fol_0000110.smt2";
    SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
    LiaStar formula = smtToSqlSolver.translateFile(filename);
    System.out.println("formula:\n" + formula);

    var result = LiaSolver.solveWithConfig(formula, LIA_SOLVER_CONFIGS[1]);
    System.out.println("result: " + result);
    assertEquals(LiaSolverStatus.UNSAT, result);
  }

  @Test
  public void runAllMapaBenchmarks()
  {
    String[] directories = {"/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_mapa",
        "/home/mudathir/all/sls-reachability/benchmarks/bapa/card/cvc5_mapa"};
    long timeoutSeconds = 100;
    String outputCsv = "sql_mapa.csv";

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
    String[] directories = {"/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_bapa",
        "/home/mudathir/all/sls-reachability/benchmarks/bapa/card/cvc5_bapa"};
    long timeoutSeconds = 100;
    String outputCsv = "sql_bapa.csv";

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
}