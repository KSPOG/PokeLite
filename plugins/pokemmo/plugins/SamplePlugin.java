package pokemmo.plugins;

import pokemmo.Client;
import pokemmo.ClientPlugin;

/**
 * Demonstration plugin used to verify plugin discovery and execution.
 */
public final class SamplePlugin implements ClientPlugin {
    @Override
    public void run(Client client) {
        Client.getLogger().info("SamplePlugin executed");
    }
}
