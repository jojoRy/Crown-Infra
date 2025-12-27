package kr.crownrpg.infra.core.database;

import kr.crownrpg.infra.api.database.DatabaseException;
import kr.crownrpg.infra.api.database.Transaction;

import java.sql.Connection;

public final class JdbcTransaction extends JdbcQueryExecutor implements Transaction {

    private boolean rollbackOnly = false;

    public JdbcTransaction(Connection connection) {
        super(connection);
    }

    @Override
    public void commit() {
        try {
            if (rollbackOnly) {
                connection.rollback();
            } else {
                connection.commit();
            }
        } catch (Exception e) {
            throw new DatabaseException("commit failed", e);
        }
    }

    @Override
    public void rollback() {
        try {
            rollbackOnly = true;
            connection.rollback();
        } catch (Exception e) {
            throw new DatabaseException("rollback failed", e);
        }
    }

    public boolean isRollbackOnly() {
        return rollbackOnly;
    }
}
