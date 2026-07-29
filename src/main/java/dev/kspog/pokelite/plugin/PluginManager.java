package dev.kspog.pokelite.plugin;

import dev.kspog.pokelite.plugin.builtin.ClientStatusPlugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

public final class PluginManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PluginManager.class.getName());

    private final Map<String, PokeLitePlugin> plugins = new LinkedHashMap<>();
    private final Preferences preferences = Preferences.userRoot().node("dev/kspog/pokelite/plugins");
    private final Path externalPluginDirectory;
    private URLClassLoader externalClassLoader;

    public PluginManager(Path externalPluginDirectory) {
        this.externalPluginDirectory = externalPluginDirectory.toAbsolutePath().normalize();
        register(new ClientStatusPlugin(this.externalPluginDirectory.getParent().resolve("poke")));
        loadClasspathPlugins();
        loadExternalPlugins();
        startEnabledPlugins();
    }

    public Collection<PokeLitePlugin> getPlugins() {
        return plugins.values().stream()
            .sorted(Comparator.comparing(PokeLitePlugin::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public boolean isEnabled(PokeLitePlugin plugin) {
        return preferences.getBoolean(plugin.getId(), plugin.isEnabledByDefault());
    }

    public void setEnabled(PokeLitePlugin plugin, boolean enabled) {
        boolean current = isEnabled(plugin);
        if (current == enabled) {
            return;
        }

        try {
            if (enabled) {
                plugin.onEnable();
            } else {
                plugin.onDisable();
            }
            preferences.putBoolean(plugin.getId(), enabled);
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "Unable to change plugin state: " + plugin.getName(), error);
            throw new IllegalStateException("Unable to change plugin state: " + plugin.getName(), error);
        }
    }

    private void register(PokeLitePlugin plugin) {
        PokeLitePlugin previous = plugins.putIfAbsent(plugin.getId(), plugin);
        if (previous != null) {
            LOGGER.warning("Ignoring duplicate plugin id: " + plugin.getId());
        }
    }

    private void loadClasspathPlugins() {
        ServiceLoader.load(PokeLitePlugin.class).forEach(this::register);
    }

    private void loadExternalPlugins() {
        try {
            Files.createDirectories(externalPluginDirectory);
            List<URL> urls = new ArrayList<>();

            try (Stream<Path> paths = Files.list(externalPluginDirectory)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .map(path -> {
                        try {
                            return path.toUri().toURL();
                        } catch (IOException error) {
                            throw new IllegalStateException(error);
                        }
                    })
                    .forEach(urls::add);
            }

            if (urls.isEmpty()) {
                return;
            }

            externalClassLoader = new URLClassLoader(
                urls.toArray(URL[]::new),
                PokeLitePlugin.class.getClassLoader()
            );
            ServiceLoader.load(PokeLitePlugin.class, externalClassLoader).forEach(this::register);
        } catch (IOException | RuntimeException error) {
            LOGGER.log(Level.WARNING, "Unable to load external plugins", error);
        }
    }

    private void startEnabledPlugins() {
        for (PokeLitePlugin plugin : plugins.values()) {
            if (!isEnabled(plugin)) {
                continue;
            }
            try {
                plugin.onEnable();
            } catch (Exception error) {
                LOGGER.log(Level.WARNING, "Unable to start plugin: " + plugin.getName(), error);
            }
        }
    }

    @Override
    public void close() {
        for (PokeLitePlugin plugin : plugins.values()) {
            if (!isEnabled(plugin)) {
                continue;
            }
            try {
                plugin.onDisable();
            } catch (Exception error) {
                LOGGER.log(Level.FINE, "Unable to stop plugin: " + plugin.getName(), error);
            }
        }

        if (externalClassLoader != null) {
            try {
                externalClassLoader.close();
            } catch (IOException error) {
                LOGGER.log(Level.FINE, "Unable to close external plugin class loader", error);
            }
        }
    }
}
