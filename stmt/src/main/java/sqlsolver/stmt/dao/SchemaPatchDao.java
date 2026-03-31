package sqlsolver.stmt.dao;

import java.util.List;
import sqlsolver.sql.schema.SchemaPatch;
import sqlsolver.stmt.dao.internal.DbSchemaPatchDao;

public interface SchemaPatchDao
{
  List<SchemaPatch> findByApp(String appName);

  void save(SchemaPatch patch);

  void truncate(String app);

  void beginBatch();

  void endBatch();

  static SchemaPatchDao instance()
  {
    return DbSchemaPatchDao.instance();
  }
}
