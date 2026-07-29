package dev.kspog.pokelite.plugin;

import javax.swing.JComponent;

public interface PokeLitePlugin {
    String getId();

    String getName();

    String getDescription();

    default boolean isEnabledByDefault() {
        return false;
    }

    JComponent createPanel();

    default void onEnable() throws Exception {
    }

    default void onDisable() throws Exception {
    }
}
