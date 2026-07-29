package dev.kspog.pokelite.core;

import dev.kspog.pokelite.api.ClientApi;
import dev.kspog.pokelite.api.ClientCapability;
import dev.kspog.pokelite.api.ClientConnectionState;
import dev.kspog.pokelite.api.ClientEvents;
import dev.kspog.pokelite.api.EventBus;
import dev.kspog.pokelite.api.GameDataSink;
import dev.kspog.pokelite.api.GameSnapshot;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultClientApi implements ClientApi, GameDataSink {
    private final EventBus eventBus;
    private final AtomicReference<ClientConnectionState> connectionState =
        new AtomicReference<>(ClientConnectionState.STOPPED);
    private final AtomicReference<GameSnapshot> snapshot =
        new AtomicReference<>(GameSnapshot.empty("No game data provider is active"));
    private final AtomicReference<Set<ClientCapability>> capabilities =
        new AtomicReference<>(Set.of(ClientCapability.PROCESS_STATE));

    public DefaultClientApi(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    @Override
    public ClientConnectionState getConnectionState() {
        return connectionState.get();
    }

    @Override
    public GameSnapshot getSnapshot() {
        return snapshot.get();
    }

    @Override
    public Set<ClientCapability> getCapabilities() {
        return capabilities.get();
    }

    public void setConnectionState(ClientConnectionState state) {
        Objects.requireNonNull(state);
        ClientConnectionState previous = connectionState.getAndSet(state);
        if (previous != state) {
            eventBus.post(new ClientEvents.ConnectionStateChanged(previous, state));
        }
    }

    @Override
    public void publish(GameSnapshot next) {
        Objects.requireNonNull(next);
        GameSnapshot previous = snapshot.getAndSet(next);
        eventBus.post(new ClientEvents.SnapshotUpdated(previous, next));

        if (!Objects.equals(previous.money(), next.money())) {
            eventBus.post(new ClientEvents.MoneyChanged(previous.money(), next.money()));
        }
        if (!Objects.equals(previous.experience(), next.experience())) {
            eventBus.post(new ClientEvents.ExperienceChanged(previous.experience(), next.experience()));
        }
    }

    @Override
    public void updateCapabilities(String providerId, Set<ClientCapability> providerCapabilities) {
        EnumSet<ClientCapability> merged = EnumSet.of(ClientCapability.PROCESS_STATE);
        if (providerCapabilities != null) {
            merged.addAll(providerCapabilities);
        }
        Set<ClientCapability> immutable = Set.copyOf(merged);
        capabilities.set(immutable);
        eventBus.post(new ClientEvents.CapabilitiesChanged(
            providerId == null ? "unknown" : providerId,
            immutable
        ));
    }
}
