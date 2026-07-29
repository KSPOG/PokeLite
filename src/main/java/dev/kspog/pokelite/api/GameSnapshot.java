package dev.kspog.pokelite.api;

import java.time.Instant;

/**
 * Immutable point-in-time view of the game data currently available to plugins.
 */
public record GameSnapshot(
    Long money,
    Long sessionMoneyChange,
    ExperienceSnapshot experience,
    String providerId,
    String status,
    Instant capturedAt
) {
    public static GameSnapshot empty(String status) {
        return new GameSnapshot(null, null, null, "none", status, Instant.now());
    }
}
