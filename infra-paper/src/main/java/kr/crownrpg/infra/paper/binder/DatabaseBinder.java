package kr.crownrpg.infra.paper.binder;

import kr.crownrpg.infra.api.database.DatabaseService;
import kr.crownrpg.infra.core.database.HikariDatabaseService;
import kr.crownrpg.infra.paper.config.DatabaseYamlConfig;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DatabaseBinder {

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
            logger.info("DatabaseBinder started");
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
            logger.log(Level.SEVERE, "Failed to stop DatabaseBinder", e);
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
}
