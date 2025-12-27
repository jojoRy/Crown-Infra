package kr.crownrpg.infra.api.database;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Abstraction over a single result row without binding to JDBC APIs.
 */
public interface ResultRow {

    String getString(String columnLabel);

    Integer getInt(String columnLabel);

    Long getLong(String columnLabel);

    Double getDouble(String columnLabel);

    Boolean getBoolean(String columnLabel);

    byte[] getBytes(String columnLabel);

    LocalDate getLocalDate(String columnLabel);

    LocalDateTime getLocalDateTime(String columnLabel);

    Instant getInstant(String columnLabel);

    <T> Optional<T> getObject(String columnLabel, Class<T> type);
}
