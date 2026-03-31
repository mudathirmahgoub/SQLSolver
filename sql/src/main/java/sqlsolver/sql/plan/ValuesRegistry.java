package sqlsolver.sql.plan;

import java.util.List;
import sqlsolver.sql.schema.Column;

public interface ValuesRegistry
{
  Values valuesOf(int nodeId);

  int initiatorOf(Value value);

  Column columnOf(Value value);

  Expression exprOf(Value value);

  Values valueRefsOf(Expression expr);

  void bindValues(int nodeId, List<Value> values);

  void bindValueRefs(Expression expr, List<Value> valueRefs);

  void bindExpr(Value value, Expression expr);
}
