package dev.kspog.pokelite.api;

import java.nio.file.Path;
import java.util.logging.Logger;

public interface PluginContext {
    ClientApi client();
    EventBus events();
    Path projectRoot();
    Path dataDirectory(String pluginId);
    Logger logger(String pluginId);
}
