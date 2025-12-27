package kr.crownrpg.infra.api.database;

@FunctionalInterface
public interface TransactionCallback<T> {

    T doInTransaction(DbSession session);
}
