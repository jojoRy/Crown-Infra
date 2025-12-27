package kr.crownrpg.infra.api.context;

import kr.crownrpg.infra.api.Preconditions;

/**
 * Immutable context carrying environment and server identifier information.
 */
public record InfraContext(String environment, String serverId) {

    public InfraContext {
        Preconditions.checkNotBlank(environment, "environment");
        Preconditions.checkNotBlank(serverId, "serverId");
    }

    public static InfraContext of(String environment, String serverId) {
        return new InfraContext(environment, serverId);
    }

    @Override
    public String toString() {
        return "InfraContext{" +
                "environment='" + environment + '\'' +
                ", serverId='" + serverId + '\'' +
                '}';
    }
}
