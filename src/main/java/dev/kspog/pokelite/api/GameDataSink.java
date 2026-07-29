package dev.kspog.pokelite.api;

import java.util.Set;

/**
 * Destination used by data providers to publish read-only snapshots.
 */
public interface GameDataSink {
    void publish(GameSnapshot snapshot);

    void updateCapabilities(String providerId, Set<ClientCapability> capabilities);
}
