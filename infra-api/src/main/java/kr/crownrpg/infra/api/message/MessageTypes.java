package kr.crownrpg.infra.api.message;

import kr.crownrpg.infra.api.Preconditions;

/**
 * Canonical naming rules for message types that travel through {@link InfraMessage}.
 * <p>
 * Message types remain plain strings (no enum) to keep extensibility across modules. The recommended
 * structure is {@code "<domain>.<action>"} using lowercase segments with hyphen avoidance. Examples:
 * <ul>
 *     <li>{@code "profile.sync"}</li>
 *     <li>{@code "party.invite"}</li>
 *     <li>{@code "chat.broadcast"}</li>
 * </ul>
 * <p>
 * Domain segment represents the bounded context (e.g., profile, party, chat) and action describes the
 * command or event name (e.g., sync, invite, broadcast). Additional hierarchy may be represented using
 * dots (e.g., {@code "economy.balance.update"}) but should avoid ambiguous casing or spacing.
 */
public final class MessageTypes {

    private MessageTypes() {
    }

    /**
     * Builds a message type string following the {@code <domain>.<action>} convention.
     *
     * @param domain non-blank domain segment
     * @param action non-blank action segment
     * @return joined message type string
     */
    public static String compose(String domain, String action) {
        Preconditions.checkNotBlank(domain, "domain");
        Preconditions.checkNotBlank(action, "action");
        return domain + "." + action;
    }
}
