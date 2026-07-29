package dev.kspog.pokelite.core;

import dev.kspog.pokelite.api.ClientApi;
import dev.kspog.pokelite.api.EventBus;
import dev.kspog.pokelite.api.PluginContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public final class DefaultPluginContext implements PluginContext {
    private final ClientApi clientApi;
    private final EventBus eventBus;
    private final Path projectRoot;

    public DefaultPluginContext(ClientApi clientApi, EventBus eventBus, Path projectRoot) {
        this.clientApi = Objects.requireNonNull(clientApi);
        this.eventBus = Objects.requireNonNull(eventBus);
        this.projectRoot = Objects.requireNonNull(projectRoot).toAbsolutePath().normalize();
    }

    @Override
    public ClientApi client() {
        return clientApi;
    }

    @Override
    public EventBus events() {
        return eventBus;
    }

    @Override
    public Path projectRoot() {
        return projectRoot;
    }

    @Override
    public Path dataDirectory(String pluginId) {
        String safeId = Objects.requireNonNull(pluginId)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "-");
        Path directory = projectRoot.resolve("data").resolve("plugins").resolve(safeId);
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create plugin data directory: " + directory, error);
        }
        return directory;
    }

    @Override
    public Logger logger(String pluginId) {
        return Logger.getLogger("dev.kspog.pokelite.plugin." + pluginId);
    }
}
