package sqlsolver.api.lia;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import sqlsolver.api.entry.Verification;
import sqlsolver.sql.plan.Value;
import sqlsolver.superopt.liastar.LiaSolver;
import sqlsolver.superopt.liastar.LiaStar;
import sqlsolver.superopt.logic.VerificationResult;

public class SqlSolverTest
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
  private final LiaStar z = LiaStar.mkVar(false, "z", Value.TYPE_INT);
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
  private final LiaStar cMinus3 = LiaStar.mkConst(false, -3);

  @Test
  public void f2()
  {
    // y >= x - 3 and y >= 3 - x
    // 5x + 2 y >= 17
    var ineq1 = LiaStar.mkLe(false, LiaStar.mkPlus(false, x, cMinus3), y);
    var minusX = LiaStar.mkMul(false, cMinus1, x);
    var ineq2 = LiaStar.mkLe(false, LiaStar.mkPlus(false, c3, minusX), y);
    var body = LiaStar.mkConjunction(false, Arrays.asList(new LiaStar[] {ineq1, ineq2}));

    // z = 1
    var zEquals1 = LiaStar.mkEq(false, z, c1);

    var sum = LiaStar.mkSum(
        false, Arrays.asList(new String[] {"z","z"}), Arrays.asList(new String[] {"x", "y"}), body);
    var f = LiaStar.mkConjunction(false, Arrays.asList(new LiaStar[] {zEquals1, sum}));
    var result = LiaSolver.solveWithConfig(f, LIA_SOLVER_CONFIGS[1]);
    System.out.print(result);
  }

  @Test
  void simpleUnion() throws IOException
  {
    String sql1 = "SELECT name from a";
    String sql2 = "select name from a union all select name from a";
    String schema = "CREATE TABLE a(ssn int, name int);";

    VerificationResult result = Verification.verify(sql1, sql2, schema);
    assertEquals(VerificationResult.EQ, result);
  }

  @Test
  void count() throws IOException
  {
    String sql1 = "SELECT count(name) from a";
    String sql2 = "select count(*) from a";
    String schema = "CREATE TABLE a(ssn int, name int);";

    VerificationResult result = Verification.verify(sql1, sql2, schema);
    assertEquals(VerificationResult.EQ, result);
  }

  @Test
  void paper2020() throws IOException
  {
    String sql1 = "SELECT count(name) from a";
    String sql2 = "select count(*) from a";
    String schema = "CREATE TABLE a(ssn int, name int);";

    VerificationResult result = Verification.verify(sql1, sql2, schema);
    assertEquals(VerificationResult.EQ, result);
  }
}
