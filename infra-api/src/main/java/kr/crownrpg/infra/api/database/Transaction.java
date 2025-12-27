package kr.crownrpg.infra.api.database;

public interface Transaction extends QueryExecutor {

    void commit();

    void rollback();
}
