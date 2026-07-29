package dev.kspog.pokelite.api;

/**
 * Experience values reported by a data provider.
 * A null required value means the provider cannot determine the level target.
 */
public record ExperienceSnapshot(long current, Long required, long sessionGained) {
}
