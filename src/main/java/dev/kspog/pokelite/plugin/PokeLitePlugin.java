package dev.kspog.pokelite.plugin;

import dev.kspog.pokelite.api.PluginContext;

import javax.swing.JComponent;

/**
 * Public plugin contract. External plugins are initialized once with a stable
 * PluginContext before their saved enabled state is applied.
 */
public interface PokeLitePlugin {
    String getId();

    String getName();

    String getDescription();

    default boolean isEnabledByDefault() {
        return false;
    }

    default void initialize(PluginContext context) throws Exception {
    }

    JComponent createPanel();

    default void onEnable() throws Exception {
    }

    default void onDisable() throws Exception {
    }
}
