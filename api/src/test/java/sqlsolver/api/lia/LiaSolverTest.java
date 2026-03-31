package sqlsolver.api.lia;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import sqlsolver.api.entry.Verification;
import sqlsolver.superopt.logic.VerificationResult;

public class LiaSolverTest
{
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
}
