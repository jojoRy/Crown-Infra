package kr.crownrpg.infra.api.database;

@FunctionalInterface
public interface RowMapper<T> {

    T map(ResultRow row);
}
