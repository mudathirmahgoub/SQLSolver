package sqlsolver.superopt.liastar;

import java.util.Properties;

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
        "/home/mudathir/all/sls-reachability/benchmarks/bapa/arith/cvc5_bapa/fol_0000001.smt2";
    SmtToSqlSolver smtToSqlSolver = new SmtToSqlSolver();
    LiaStar formula = smtToSqlSolver.translateFile(filename);
    System.out.println("formula:\n" + formula);
    for (var properties : LIA_SOLVER_CONFIGS)
    {
      var result = LiaSolver.solveWithConfig(formula, properties);
      System.out.println("result: " + result);
      // assertEquals(LiaSolverStatus.UNSAT, result);
    }
  }
}