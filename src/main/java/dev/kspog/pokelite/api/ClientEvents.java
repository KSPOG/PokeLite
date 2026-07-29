package dev.kspog.pokelite.api;

import java.util.Set;

/**
 * Typed events published through the PokeLite event bus.
 */
public final class ClientEvents {
    private ClientEvents() {
    }

    public record ConnectionStateChanged(
        ClientConnectionState previous,
        ClientConnectionState current
    ) {
    }

    public record SnapshotUpdated(GameSnapshot previous, GameSnapshot current) {
    }

    public record MoneyChanged(Long previous, Long current) {
    }

    public record ExperienceChanged(
        ExperienceSnapshot previous,
        ExperienceSnapshot current
    ) {
    }

    public record CapabilitiesChanged(
        String providerId,
        Set<ClientCapability> capabilities
    ) {
        public CapabilitiesChanged {
            capabilities = Set.copyOf(capabilities);
        }
    }
}
