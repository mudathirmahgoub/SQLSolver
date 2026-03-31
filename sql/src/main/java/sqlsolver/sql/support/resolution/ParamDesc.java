package sqlsolver.sql.support.resolution;

import java.util.List;
import sqlsolver.sql.ast.SqlNode;

public interface ParamDesc
{
  SqlNode node();

  List<ParamModifier> modifiers();

  int index();

  void setIndex(int idx);
}
