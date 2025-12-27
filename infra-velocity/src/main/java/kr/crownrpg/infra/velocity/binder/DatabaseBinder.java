package kr.crownrpg.infra.velocity.binder;

import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.core.database.HikariDatabaseService;
import kr.crownrpg.infra.velocity.config.DatabaseYamlConfig;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DatabaseBinder implements AutoCloseable {

    private static final String LOG_PREFIX = "[CrownInfra-Velocity] ";

    private final Logger logger;
    private final DatabaseYamlConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private HikariDatabaseService service;

    public DatabaseBinder(Logger logger, DatabaseYamlConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public synchronized void start() {
        if (started.get()) {
            return;
        }
        try {
            this.service = new HikariDatabaseService(config.toDatabaseConfig());
            service.start();
            started.set(true);
            logger.info(LOG_PREFIX + "데이터베이스 연결이 성공적으로 완료되었습니다.");
        } catch (Exception e) {
            started.set(false);
            throw e;
        }
    }

    public synchronized void stop() {
        if (!started.get()) {
            return;
        }
        try {
            if (service != null) {
                service.stop();
            }
        } catch (Exception e) {
            logger.error(LOG_PREFIX + "데이터베이스 연결 종료 중 오류가 발생했습니다.", e);
        } finally {
            started.set(false);
        }
    }

    public DatabaseService getService() {
        return service;
    }

    public boolean isStarted() {
        return started.get();
    }

    @Override
    public void close() {
        stop();
    }
}
