package kr.crownrpg.infra.api.lifecycle;

/**
 * Standard lifecycle contract to start and stop resources.
 */
public interface ManagedLifecycle {

    void start();

    void stop();
}
