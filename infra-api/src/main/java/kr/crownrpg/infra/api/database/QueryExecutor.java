package kr.crownrpg.infra.api.database;

import java.util.List;

public interface QueryExecutor {

    int executeUpdate(String sql, Object... params);

    List<Row> executeQuery(String sql, Object... params);
}
