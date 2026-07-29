package dev.kspog.pokelite.api;

import java.util.Set;

/**
 * Replaceable source of read-only game data. Implementations may use an
 * official API, documented logs, OCR, or another explicitly authorized source.
 */
public interface GameDataProvider extends AutoCloseable {
    String getId();

    String getDisplayName();

    Set<ClientCapability> getCapabilities();

    boolean isRunning();

    void start(GameDataSink sink) throws Exception;

    void stop() throws Exception;

    @Override
    default void close() throws Exception {
        stop();
    }
}
