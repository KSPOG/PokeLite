package dev.kspog.pokelite.gamedata;

import java.time.Instant;

public record GameDataSnapshot(
    Long money,
    Long moneyChange,
    ExperienceValue experience,
    long sessionExperienceGained,
    String rawMoneyText,
    String rawExperienceText,
    String status,
    Instant capturedAt
) {
    public static GameDataSnapshot waiting(String status) {
        return new GameDataSnapshot(
            null,
            null,
            null,
            0L,
            "",
            "",
            status,
            Instant.now()
        );
    }
}
