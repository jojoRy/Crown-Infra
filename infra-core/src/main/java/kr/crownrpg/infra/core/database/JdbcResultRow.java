package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.ResultRow;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * ResultRow implementation that wraps a JDBC {@link ResultSet}.
 */
public class JdbcResultRow implements ResultRow {

    private final ResultSet resultSet;

    public JdbcResultRow(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    @Override
    public String getString(String columnLabel) {
        try {
            return resultSet.getString(columnLabel);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer getInt(String columnLabel) {
        try {
            int value = resultSet.getInt(columnLabel);
            return resultSet.wasNull() ? null : value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Long getLong(String columnLabel) {
        try {
            long value = resultSet.getLong(columnLabel);
            return resultSet.wasNull() ? null : value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Double getDouble(String columnLabel) {
        try {
            double value = resultSet.getDouble(columnLabel);
            return resultSet.wasNull() ? null : value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Boolean getBoolean(String columnLabel) {
        try {
            boolean value = resultSet.getBoolean(columnLabel);
            return resultSet.wasNull() ? null : value;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes(String columnLabel) {
        try {
            return resultSet.getBytes(columnLabel);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LocalDate getLocalDate(String columnLabel) {
        try {
            java.sql.Date date = resultSet.getDate(columnLabel);
            return date == null ? null : date.toLocalDate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public LocalDateTime getLocalDateTime(String columnLabel) {
        try {
            Timestamp ts = resultSet.getTimestamp(columnLabel);
            return ts == null ? null : ts.toLocalDateTime();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Instant getInstant(String columnLabel) {
        try {
            Timestamp ts = resultSet.getTimestamp(columnLabel);
            return ts == null ? null : ts.toInstant();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> Optional<T> getObject(String columnLabel, Class<T> type) {
        try {
            return Optional.ofNullable(resultSet.getObject(columnLabel, type));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
