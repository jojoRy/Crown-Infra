package kr.crownrpg.infra.core.context;

import kr.crownrpg.infra.api.context.InfraContext;
import kr.crownrpg.infra.api.context.ServerKind;

import java.util.Objects;

public final class DefaultInfraContext implements InfraContext {

    private final String environment;
    private final String serverId;
    private final ServerKind kind;

    public DefaultInfraContext(String environment, String serverId, ServerKind kind) {
        this.environment = requireNotBlank(environment, "environment");
        this.serverId = requireNotBlank(serverId, "serverId");
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    @Override
    public String environment() {
        return environment;
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public ServerKind kind() {
        return kind;
    }

    private static String requireNotBlank(String v, String name) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException(name + " is blank");
        return v;
    }
}
