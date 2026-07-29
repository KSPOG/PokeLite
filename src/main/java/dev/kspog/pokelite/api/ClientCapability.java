package dev.kspog.pokelite.api;

/**
 * Game-state capabilities which may be supplied by the active data provider.
 * Plugins must check capabilities before relying on optional game data.
 */
public enum ClientCapability {
    PROCESS_STATE,
    MONEY,
    EXPERIENCE,
    LOCATION,
    PARTY,
    INVENTORY,
    BATTLE,
    ENCOUNTER
}
