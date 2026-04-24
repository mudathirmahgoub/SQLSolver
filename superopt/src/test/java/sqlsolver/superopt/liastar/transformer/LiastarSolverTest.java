package sqlsolver.superopt.liastar.transformer;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import sqlsolver.sql.plan.Value;
import sqlsolver.superopt.liastar.LiaSolver;
import sqlsolver.superopt.liastar.LiaSolverStatus;
import sqlsolver.superopt.liastar.LiaStar;

public class LiastarSolverTest
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

  private final LiaStar x = LiaStar.mkVar(false, "x", Value.TYPE_INT);
  private final LiaStar y = LiaStar.mkVar(false, "y", Value.TYPE_INT);
  private final LiaStar c17 = LiaStar.mkConst(false, 17);
  private final LiaStar c0 = LiaStar.mkConst(false, 0);
  private final LiaStar c1 = LiaStar.mkConst(false, 1);
  private final LiaStar c5 = LiaStar.mkConst(false, 5);
  private final LiaStar c2 = LiaStar.mkConst(false, 2);
  private final LiaStar c3 = LiaStar.mkConst(false, 3);
  private final LiaStar c6 = LiaStar.mkConst(false, 6);
  private final LiaStar c8 = LiaStar.mkConst(false, 8);
  private final LiaStar c20 = LiaStar.mkConst(false, 20);
  private final LiaStar c47 = LiaStar.mkConst(false, 47);
  private final LiaStar cMinus1 = LiaStar.mkConst(false, -1);
  private final LiaStar v1 = LiaStar.mkVar(false, "v1", Value.TYPE_INT);
  private final LiaStar v2 = LiaStar.mkVar(false, "v2", Value.TYPE_INT);

  @Test
  public void f2()
  {
    // 5x + 2y >= 17 ∧ 3x − y ≤ 8 ∧ 2x + 3y ≤ 20
    // 5x + 2 y >= 17
    var f2Inequility1 = LiaStar.mkLe(false,
        c17,
        LiaStar.mkPlus(false, LiaStar.mkMul(false, c5, x), LiaStar.mkMul(false, c2, y)));
    // 3x − y ≤ 8
    var f2Inequility2 = LiaStar.mkLe(false,
        LiaStar.mkPlus(false, LiaStar.mkMul(false, c3, x), LiaStar.mkMul(false, cMinus1, y)),
        c8);
    // 2x + 3y ≤ 20
    var f2Inequility3 = LiaStar.mkLe(false,
        LiaStar.mkPlus(false, LiaStar.mkMul(false, c2, x), LiaStar.mkMul(false, c3, y)),
        c20);

    var f2 =
        LiaStar.mkConjunction(false, Arrays.asList(f2Inequility1, f2Inequility2, f2Inequility3));    
    var innerVector = Arrays.asList(new String[] {"x", "y"});
    var outerVector = Arrays.asList("v1", "v2");
    var star = LiaStar.mkSum(false, outerVector, innerVector, f2);
    var v1IsOne = LiaStar.mkEq(false, v1, c1);
    var v2IsOne = LiaStar.mkEq(false, v2, c1);
    var phi = LiaStar.mkAnd(false, v1IsOne, v2IsOne);
    var formula = LiaStar.mkAnd(false, phi, star);
    System.out.println("formula:\n" + formula);
    for (var properties : LIA_SOLVER_CONFIGS)
    {
      var result = LiaSolver.solveWithConfig(formula, properties);
      System.out.println("result: " + result);
      // assertEquals(LiaSolverStatus.UNSAT, result);
    }
  }

  @Test
  public void f1()
  {
    // y + 2x ≥ 17 ∧ 6x − y ≤ 47
    // y + 2x >= 17
    var f1Inequality1 =
        LiaStar.mkLe(false, c17, LiaStar.mkPlus(false, LiaStar.mkMul(false, c2, x), y));
    // 6x − y ≤ 47
    var f1Inequality2 = LiaStar.mkLe(false,
        LiaStar.mkPlus(false, LiaStar.mkMul(false, c6, x), LiaStar.mkMul(false, cMinus1, y)),
        c47);
    var f1 = LiaStar.mkConjunction(false, Arrays.asList(f1Inequality1, f1Inequality2));
    var innerVector = Arrays.asList(new String[] {"x", "y"});
    var outerVector = Arrays.asList("v1", "v2");
    var star = LiaStar.mkSum(false, outerVector, innerVector, f1);
    var v1IsOne = LiaStar.mkEq(false, v1, c1);
    var v2IsOne = LiaStar.mkEq(false, v2, c1);
    var phi = LiaStar.mkAnd(false, v1IsOne, v2IsOne);
    var formula = LiaStar.mkAnd(false, phi, star);
    System.out.println("formula:\n" + formula);
    for (var properties : LIA_SOLVER_CONFIGS)
    {
      var result = LiaSolver.solveWithConfig(formula, properties);
      System.out.println("result: " + result);
      assertEquals(LiaSolverStatus.UNSAT, result);
    }
  }

  @Test
  public void xEquals1()
  {
    // 5x + 2y >= 17 ∧ 3x − y ≤ 8 ∧ 2x + 3y ≤ 20
    // 5x + 2 y >= 17
    var xEqual1 = LiaStar.mkEq(false, x, c1);
    var yGeq0 = LiaStar.mkLe(false, c0, y);
    var and = LiaStar.mkConjunction(false, Arrays.asList(xEqual1, yGeq0));
    SlsAugmenter augmenter = new SlsAugmenter(Arrays.asList(new String[] {"x", "y"}), and);
    System.out.println("sls: " + augmenter.sls());
    while (augmenter.augment())
    {
      System.out.println("sls: " + augmenter.sls());
    }
  }
}
