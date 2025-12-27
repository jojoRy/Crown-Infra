package kr.crownrpg.infra.api.transfer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Documentation-first contracts for realtime player transfer messaging.
 * <p>
 * Key rules:
 * <ul>
 *     <li>All transfer messages must be sent via Netty-based {@code RealtimeChannel}; Redis is not used.</li>
 *     <li>Message types follow {@code player.transfer.{stage}} using constants in {@link TransferMessageTypes}.</li>
 *     <li>Payload is JSON string only; parsing occurs in downstream modules, not in infra-api.</li>
 *     <li>Every payload must include the identifiers defined in {@link TransferPayloadKeys}.</li>
 *     <li>State transitions are forward-only as described in {@link #isValidTransition(String, String)}.</li>
 * </ul>
 * </p>
 * <p>
 * Directional flow reference:
 * <ul>
 *     <li>Paper (origin) → Velocity: {@code player.transfer.request}</li>
 *     <li>Velocity → Paper (destination): {@code player.transfer.prepare}</li>
 *     <li>Paper (destination) → Velocity: {@code player.transfer.ready}</li>
 *     <li>Velocity → Paper (origin): {@code player.transfer.transferring}</li>
 *     <li>Velocity → Paper (destination): {@code player.transfer.complete}</li>
 *     <li>Velocity → Paper (origin) on failure: {@code player.transfer.fail}</li>
 * </ul>
 * Cancellation ({@code player.transfer.cancel}) is a terminal branch from REQUEST.</p>
 */
public final class TransferContracts {

    private static final Map<String, Set<String>> TRANSITIONS;

    static {
        Map<String, Set<String>> transitions = new HashMap<>();
        transitions.put(TransferStage.REQUEST, Set.of(TransferStage.PREPARE, TransferStage.CANCEL));
        transitions.put(TransferStage.PREPARE, Set.of(TransferStage.READY));
        transitions.put(TransferStage.READY, Set.of(TransferStage.TRANSFERRING));
        transitions.put(TransferStage.TRANSFERRING, Set.of(TransferStage.COMPLETE, TransferStage.FAIL));
        transitions.put(TransferStage.COMPLETE, Collections.emptySet());
        transitions.put(TransferStage.FAIL, Collections.emptySet());
        transitions.put(TransferStage.CANCEL, Collections.emptySet());
        TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private TransferContracts() {
    }

    /**
     * Returns an immutable set of allowed next stages for the provided current stage.
     * Unknown stages yield an empty set, keeping the contract permissive for optional extensions
     * while maintaining the canonical forward-only flow for the predefined stages.
     *
     * @param currentStage the current stage identifier
     * @return immutable set of allowed next stages (may be empty)
     */
    public static Set<String> allowedNextStages(String currentStage) {
        Set<String> next = TRANSITIONS.get(currentStage);
        if (next == null) {
            return Collections.emptySet();
        }
        return next;
    }

    /**
     * Determines whether moving from {@code fromStage} to {@code toStage} is permitted by the
     * canonical player transfer state machine. The allowed transitions are:
     * <pre>
     * REQUEST -> PREPARE -> READY -> TRANSFERRING -> COMPLETE | FAIL
     * REQUEST -> CANCEL
     * </pre>
     * Reverse transitions and post-terminal transitions (after COMPLETE/FAIL/CANCEL) are not allowed.
     *
     * @param fromStage current stage identifier (string-based)
     * @param toStage   next stage identifier (string-based)
     * @return true if the transition is allowed according to the contract
     */
    public static boolean isValidTransition(String fromStage, String toStage) {
        Set<String> next = TRANSITIONS.get(fromStage);
        return next != null && next.contains(toStage);
    }
}
