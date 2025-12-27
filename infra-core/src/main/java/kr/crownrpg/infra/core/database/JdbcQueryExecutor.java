package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.DatabaseException;
import kr.crownrpg.infra.api.database.QueryExecutor;
import kr.crownrpg.infra.api.database.Row;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class JdbcQueryExecutor implements QueryExecutor {

    protected final Connection connection;

    public JdbcQueryExecutor(Connection connection) {
        this.connection = connection;
    }

    @Override
    public int executeUpdate(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            SqlBinder.bind(ps, params);
            return ps.executeUpdate();
        } catch (Exception e) {
            throw new DatabaseException("executeUpdate failed: " + sql, e);
        }
    }

    @Override
    public List<Row> executeQuery(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            SqlBinder.bind(ps, params);

            try (ResultSet rs = ps.executeQuery()) {
                List<Row> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(JdbcRow.from(rs));
                }
                return rows;
            }
        } catch (Exception e) {
            throw new DatabaseException("executeQuery failed: " + sql, e);
        }
    }
}
