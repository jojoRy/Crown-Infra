package kr.crownrpg.infra.api.transfer;

/**
 * Canonical JSON payload keys for player transfer events.
 * <p>
 * Payload values are always provided as JSON strings in the {@link kr.crownrpg.infra.api.message.InfraMessage#payload()}
 * field. This contract does not parse JSON; it only documents the required keys so that
 * implementations across Paper and Velocity remain aligned.
 * </p>
 */
public final class TransferPayloadKeys {

    private TransferPayloadKeys() {
    }

    /** Unique transfer transaction identifier (UUID string). */
    public static final String TRANSFER_ID = "transferId";

    /** Player unique identifier (UUID string). */
    public static final String PLAYER_ID = "playerId";

    /** Origin server identifier. */
    public static final String FROM_SERVER_ID = "fromServerId";

    /** Destination server identifier. */
    public static final String TO_SERVER_ID = "toServerId";

    /** Milliseconds epoch timestamp when the event was produced. */
    public static final String TIMESTAMP = "timestamp";

    /** Optional human-readable reason or error description. */
    public static final String REASON = "reason";
}
