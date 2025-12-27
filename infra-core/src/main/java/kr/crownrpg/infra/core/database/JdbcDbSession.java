package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.DbSession;
import kr.crownrpg.infra.api.database.RowMapper;
import kr.crownrpg.infra.api.database.ResultRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-based DbSession implementation backed by PreparedStatement.
 */
public class JdbcDbSession implements DbSession {

    private final Connection connection;

    public JdbcDbSession(Connection connection) {
        this.connection = connection;
    }

    @Override
    public int executeUpdate(String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute update", e);
        }
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    ResultRow row = new JdbcResultRow(resultSet);
                    results.add(mapper.map(row));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query", e);
        }
    }

    @Override
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> results = query(sql, mapper, params);
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }

    private void bindParameters(PreparedStatement statement, Object... params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            int index = i + 1;
            if (param == null) {
                statement.setNull(index, Types.NULL);
            } else if (param instanceof LocalDate localDate) {
                statement.setDate(index, java.sql.Date.valueOf(localDate));
            } else if (param instanceof LocalDateTime localDateTime) {
                statement.setTimestamp(index, Timestamp.valueOf(localDateTime));
            } else if (param instanceof Instant instant) {
                statement.setTimestamp(index, Timestamp.from(instant));
            } else {
                statement.setObject(index, param);
            }
        }
    }
}
