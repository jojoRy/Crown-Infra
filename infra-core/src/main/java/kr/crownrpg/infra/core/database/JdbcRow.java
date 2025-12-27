package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.Row;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class JdbcRow implements Row {

    private final Map<String, Object> values;

    private JdbcRow(Map<String, Object> values) {
        this.values = values;
    }

    public static JdbcRow from(ResultSet rs) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();

        Map<String, Object> map = new HashMap<>(count * 2);
        for (int i = 1; i <= count; i++) {
            String label = md.getColumnLabel(i);
            Object value = rs.getObject(i);
            map.put(label, value);
        }
        return new JdbcRow(map);
    }

    @Override
    public String getString(String column) {
        Object v = values.get(column);
        return v == null ? null : String.valueOf(v);
    }

    @Override
    public int getInt(String column) {
        Object v = values.get(column);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    @Override
    public long getLong(String column) {
        Object v = values.get(column);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(v));
    }

    @Override
    public double getDouble(String column) {
        Object v = values.get(column);
        if (v == null) return 0.0;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }

    @Override
    public boolean getBoolean(String column) {
        Object v = values.get(column);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    @Override
    public Optional<Object> get(String column) {
        return Optional.ofNullable(values.get(column));
    }
}
