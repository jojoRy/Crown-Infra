package kr.crownrpg.infra.api.transfer;

/**
 * String-based stage identifiers for the player transfer state machine.
 * <p>
 * Each stage name is lower-case and intended to be used as the trailing segment of
 * {@code player.transfer.{stage}} message types. Enums are intentionally avoided to
 * keep the contract purely string-based and avoid binary coupling for downstream modules.
 * </p>
 * <p>
 * Stage meanings (irreversible forward flow):
 * <ul>
 *     <li>{@link #REQUEST} - A player transfer has been requested by the origin server.</li>
 *     <li>{@link #PREPARE} - The destination server is being prepared for the player.</li>
 *     <li>{@link #READY} - The destination server reports it is ready to accept the player.</li>
 *     <li>{@link #TRANSFERRING} - Connection switching is in progress.</li>
 *     <li>{@link #COMPLETE} - Transfer finished successfully.</li>
 *     <li>{@link #FAIL} - Transfer failed and should be aborted on the origin side.</li>
 *     <li>{@link #CANCEL} - Transfer was cancelled before completion.</li>
 * </ul>
 * </p>
 */
public final class TransferStage {

    private TransferStage() {
    }

    /** Stage: transfer requested by origin. */
    public static final String REQUEST = "request";

    /** Stage: destination preparation started. */
    public static final String PREPARE = "prepare";

    /** Stage: destination reports readiness. */
    public static final String READY = "ready";

    /** Stage: connection switching in progress. */
    public static final String TRANSFERRING = "transferring";

    /** Stage: transfer completed successfully. */
    public static final String COMPLETE = "complete";

    /** Stage: transfer failed. */
    public static final String FAIL = "fail";

    /** Stage: transfer cancelled. */
    public static final String CANCEL = "cancel";
}
