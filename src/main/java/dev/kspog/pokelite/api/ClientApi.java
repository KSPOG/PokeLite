package dev.kspog.pokelite.api;

import java.util.Set;

/**
 * Stable read-only client facade exposed to PokeLite plugins.
 */
public interface ClientApi {
    ClientConnectionState getConnectionState();

    GameSnapshot getSnapshot();

    Set<ClientCapability> getCapabilities();

    default boolean supports(ClientCapability capability) {
        return getCapabilities().contains(capability);
    }
}
