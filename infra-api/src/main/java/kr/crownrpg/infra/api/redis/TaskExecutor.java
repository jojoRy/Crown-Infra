package kr.crownrpg.infra.api.redis;

/**
 * 플랫폼별 메인 스레드 디스패치용
 * - Paper: Bukkit scheduler runTask(...)
 * - Velocity: proxy scheduler buildTask(...).schedule()
 */
@FunctionalInterface
public interface TaskExecutor {
    void execute(Runnable task);
}
