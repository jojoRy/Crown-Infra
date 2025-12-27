package kr.crownrpg.infra.api.database;

import java.util.List;
import java.util.Optional;

/**
 * Abstract database session provided by implementations.
 */
public interface DbSession {

    int executeUpdate(String sql, Object... params);

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

    <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params);
}
