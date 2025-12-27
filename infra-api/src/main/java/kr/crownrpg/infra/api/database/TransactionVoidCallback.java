package kr.crownrpg.infra.api.database;

@FunctionalInterface
public interface TransactionVoidCallback {

    void doInTransaction(DbSession session);
}
