package kr.crownrpg.infra.api.transfer;

import kr.crownrpg.infra.api.Preconditions;

/**
 * String-based message type contracts for player transfer events over the realtime channel.
 * <p>
 * Naming rule: {@code player.transfer.{stage}}, where {@code stage} is one of the values
 * defined in {@link TransferStage}. Enums are intentionally avoided to keep the protocol
 * fully string based and to allow downstream modules to extend with optional custom stages
 * without binary changes.
 * </p>
 */
public final class TransferMessageTypes {

    private static final String PREFIX = "player.transfer.";

    private TransferMessageTypes() {
    }

    /** Message type for {@link TransferStage#REQUEST}. */
    public static final String REQUEST = PREFIX + TransferStage.REQUEST;

    /** Message type for {@link TransferStage#PREPARE}. */
    public static final String PREPARE = PREFIX + TransferStage.PREPARE;

    /** Message type for {@link TransferStage#READY}. */
    public static final String READY = PREFIX + TransferStage.READY;

    /** Message type for {@link TransferStage#TRANSFERRING}. */
    public static final String TRANSFERRING = PREFIX + TransferStage.TRANSFERRING;

    /** Message type for {@link TransferStage#COMPLETE}. */
    public static final String COMPLETE = PREFIX + TransferStage.COMPLETE;

    /** Message type for {@link TransferStage#FAIL}. */
    public static final String FAIL = PREFIX + TransferStage.FAIL;

    /** Message type for {@link TransferStage#CANCEL}. */
    public static final String CANCEL = PREFIX + TransferStage.CANCEL;

    /**
    * Compose a message type using the canonical {@code player.transfer.{stage}} format.
    * This helper preserves a single source of truth for type formatting across modules.
    *
    * @param stage transfer stage identifier, e.g. {@link TransferStage#REQUEST}
    * @return fully qualified message type string
    * @throws IllegalArgumentException if the stage is blank
    */
    public static String compose(String stage) {
        String normalized = Preconditions.checkNotBlank(stage, "stage");
        return PREFIX + normalized.trim().toLowerCase();
    }
}
