package sqlsolver.sql.support.resolution;

import sqlsolver.sql.ast.SqlNode;
import sqlsolver.sql.schema.Column;

public interface Attribute
{
  String name();

  Relation owner();

  SqlNode expr();

  Column column();
}
