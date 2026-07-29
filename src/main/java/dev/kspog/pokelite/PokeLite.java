package dev.kspog.pokelite;

import dev.kspog.pokelite.plugin.PluginManager;
import dev.kspog.pokelite.ui.PokeLiteFrame;
import dev.kspog.pokelite.ui.UiTheme;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PokeLite {
    private static final Logger LOGGER = Logger.getLogger(PokeLite.class.getName());

    private PokeLite() {
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
            LOGGER.log(Level.SEVERE, "Uncaught error on " + thread.getName(), error)
        );

        SwingUtilities.invokeLater(() -> {
            try {
                UiTheme.install();
                Path projectRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
                PluginManager pluginManager = new PluginManager(projectRoot.resolve("plugins"));
                PokeLiteFrame frame = new PokeLiteFrame(projectRoot, pluginManager);
                frame.setVisible(true);
                frame.startClient();
            } catch (RuntimeException error) {
                LOGGER.log(Level.SEVERE, "Unable to start PokeLite", error);
                throw error;
            }
        });
    }
}
