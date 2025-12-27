package kr.crownrpg.infra.api.database;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface DatabaseClient {

    /**
     * 단일 쿼리 실행 (트랜잭션 없음)
     */
    <T> CompletableFuture<T> query(Function<QueryExecutor, T> action);

    /**
     * 트랜잭션 실행
     */
    <T> CompletableFuture<T> transaction(Function<Transaction, T> action);

    /**
     * 종료 처리
     */
    void close();
}
