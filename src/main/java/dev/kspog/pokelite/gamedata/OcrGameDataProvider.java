package dev.kspog.pokelite.gamedata;

import dev.kspog.pokelite.api.ClientCapability;
import dev.kspog.pokelite.api.ExperienceSnapshot;
import dev.kspog.pokelite.api.GameDataProvider;
import dev.kspog.pokelite.api.GameDataSink;
import dev.kspog.pokelite.api.GameSnapshot;

import java.awt.Canvas;
import java.util.Set;

/**
 * Optional read-only OCR provider. Plugins consume its output only through
 * ClientApi and therefore do not depend on OCR implementation details.
 */
public final class OcrGameDataProvider implements GameDataProvider {
    private static final String ID = "screen-ocr";
    private static final Set<ClientCapability> CAPABILITIES = Set.of(
        ClientCapability.MONEY,
        ClientCapability.EXPERIENCE
    );

    private final GameDataService service;
    private volatile GameDataSink sink;

    public OcrGameDataProvider(Canvas gameCanvas) {
        service = new GameDataService(gameCanvas);
        service.addListener(this::forward);
    }

    public GameDataService getService() {
        return service;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Screen OCR";
    }

    @Override
    public Set<ClientCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean isRunning() {
        return service.isRunning();
    }

    @Override
    public synchronized void start(GameDataSink sink) {
        this.sink = sink;
        sink.updateCapabilities(ID, CAPABILITIES);
        service.start();
    }

    @Override
    public synchronized void stop() {
        service.stop();
    }

    @Override
    public synchronized void close() {
        service.close();
        sink = null;
    }

    private void forward(GameDataSnapshot source) {
        GameDataSink currentSink = sink;
        if (currentSink == null) {
            return;
        }

        ExperienceSnapshot experience = source.experience() == null
            ? null
            : new ExperienceSnapshot(
                source.experience().current(),
                source.experience().required(),
                source.sessionExperienceGained()
            );

        currentSink.publish(new GameSnapshot(
            source.money(),
            source.moneyChange(),
            experience,
            ID,
            source.status(),
            source.capturedAt()
        ));
    }
}
