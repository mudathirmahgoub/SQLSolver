package sqlsolver.sql.support.resolution;

import sqlsolver.sql.ast.SqlNode;
import sqlsolver.sql.schema.Column;

class ColumnAttribute extends AttributeBase
{
  private final Column column;

  ColumnAttribute(Relation owner, Column column)
  {
    super(owner, column.name());
    this.column = column;
  }

  @Override
  public SqlNode expr()
  {
    return null;
  }

  @Override
  public Column column()
  {
    return column;
  }
}
