package dev.kspog.pokelite.gamedata;

import java.awt.AWTException;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public final class GameDataService implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(GameDataService.class.getName());
    private static final long POLL_SECONDS = 2L;

    private final Canvas gameCanvas;
    private final Preferences preferences = Preferences.userRoot().node("dev/kspog/pokelite/game-data");
    private final Map<GameDataField, CaptureRegion> regions = new EnumMap<>(GameDataField.class);
    private final CopyOnWriteArrayList<Consumer<GameDataSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pokelite-game-data");
        thread.setDaemon(true);
        return thread;
    });

    private volatile OcrEngine ocrEngine;
    private volatile ScheduledFuture<?> pollingTask;
    private volatile Long moneyBaseline;
    private volatile Long previousExperience;
    private volatile Long previousExperienceRequired;
    private volatile long sessionExperienceGained;
    private volatile GameDataSnapshot latestSnapshot = GameDataSnapshot.waiting("OCR is stopped");

    public GameDataService(Canvas gameCanvas) {
        this.gameCanvas = Objects.requireNonNull(gameCanvas);
        this.ocrEngine = new TesseractOcrEngine(
            preferences.get("tesseract.executable", TesseractOcrEngine.defaultExecutable())
        );
        loadRegions();
    }

    public void addListener(Consumer<GameDataSnapshot> listener) {
        listeners.add(Objects.requireNonNull(listener));
        listener.accept(latestSnapshot);
    }

    public synchronized boolean isRunning() {
        return pollingTask != null && !pollingTask.isCancelled();
    }

    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        if (regions.isEmpty()) {
            publish(GameDataSnapshot.waiting("Calibrate a Money or Experience region first"));
            return;
        }
        if (!ocrEngine.isAvailable()) {
            publish(GameDataSnapshot.waiting("Tesseract was not found: " + ocrEngine.describe()));
            return;
        }

        publish(GameDataSnapshot.waiting("Starting read-only OCR capture..."));
        pollingTask = executor.scheduleWithFixedDelay(
            this::scanSafely,
            0,
            POLL_SECONDS,
            TimeUnit.SECONDS
        );
    }

    public synchronized void stop() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
        }
        publish(copyWithStatus(latestSnapshot, "OCR is stopped"));
    }

    public void scanNow() {
        executor.submit(this::scanSafely);
    }

    public synchronized void resetSession() {
        moneyBaseline = null;
        previousExperience = null;
        previousExperienceRequired = null;
        sessionExperienceGained = 0L;
        publish(copyWithStatus(latestSnapshot, "Session counters reset"));
    }

    public synchronized void setTesseractExecutable(String executable) {
        OcrEngine replacement = new TesseractOcrEngine(executable);
        ocrEngine = replacement;
        preferences.put("tesseract.executable", replacement.describe());
        publish(copyWithStatus(latestSnapshot, "OCR executable set to " + replacement.describe()));
    }

    public String getTesseractExecutable() {
        return ocrEngine.describe();
    }

    public boolean isTesseractAvailable() {
        return ocrEngine.isAvailable();
    }

    public synchronized CaptureRegion getRegion(GameDataField field) {
        return regions.get(field);
    }

    public synchronized void setRegion(GameDataField field, CaptureRegion region) {
        regions.put(field, region);
        preferences.put(regionKey(field), region.serialize());
        publish(copyWithStatus(latestSnapshot, field.getDisplayName() + " region calibrated"));
    }

    public BufferedImage captureGameCanvas() throws AWTException {
        if (!gameCanvas.isShowing() || gameCanvas.getWidth() <= 0 || gameCanvas.getHeight() <= 0) {
            throw new IllegalStateException("The embedded game window is not visible");
        }

        Point location = gameCanvas.getLocationOnScreen();
        Rectangle bounds = new Rectangle(
            location.x,
            location.y,
            gameCanvas.getWidth(),
            gameCanvas.getHeight()
        );
        return new Robot().createScreenCapture(bounds);
    }

    private void scanSafely() {
        try {
            scan();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            LOGGER.log(Level.FINE, "Unable to read game data", error);
            publish(copyWithStatus(latestSnapshot, "OCR error: " + message(error)));
        }
    }

    private void scan() throws Exception {
        BufferedImage fullCapture = captureGameCanvas();
        CaptureRegion moneyRegion;
        CaptureRegion experienceRegion;
        OcrEngine engine;

        synchronized (this) {
            moneyRegion = regions.get(GameDataField.MONEY);
            experienceRegion = regions.get(GameDataField.EXPERIENCE);
            engine = ocrEngine;
        }

        String rawMoney = "";
        String rawExperience = "";
        Long money = null;
        ExperienceValue experience = null;

        if (moneyRegion != null) {
            rawMoney = engine.recognize(crop(fullCapture, moneyRegion), GameDataField.MONEY);
            money = GameDataParser.parseMoney(rawMoney);
        }

        if (experienceRegion != null) {
            rawExperience = engine.recognize(crop(fullCapture, experienceRegion), GameDataField.EXPERIENCE);
            experience = GameDataParser.parseExperience(rawExperience);
        }

        Long moneyChange;
        synchronized (this) {
            if (money != null && moneyBaseline == null) {
                moneyBaseline = money;
            }
            moneyChange = money == null || moneyBaseline == null ? null : money - moneyBaseline;
            updateExperienceSession(experience);
        }

        String status = buildStatus(money, experience);
        publish(new GameDataSnapshot(
            money,
            moneyChange,
            experience,
            sessionExperienceGained,
            rawMoney,
            rawExperience,
            status,
            java.time.Instant.now()
        ));
    }

    private synchronized void updateExperienceSession(ExperienceValue current) {
        if (current == null) {
            return;
        }

        if (previousExperience != null) {
            long delta;
            if (current.current() >= previousExperience) {
                delta = current.current() - previousExperience;
            } else if (previousExperienceRequired != null && previousExperienceRequired >= previousExperience) {
                delta = (previousExperienceRequired - previousExperience) + current.current();
            } else {
                delta = 0L;
            }

            if (delta >= 0 && delta < 100_000_000L) {
                sessionExperienceGained += delta;
            }
        }

        previousExperience = current.current();
        previousExperienceRequired = current.required();
    }

    private static BufferedImage crop(BufferedImage source, CaptureRegion region) {
        Rectangle pixels = region.toPixels(source.getWidth(), source.getHeight());
        return source.getSubimage(pixels.x, pixels.y, pixels.width, pixels.height);
    }

    private static String buildStatus(Long money, ExperienceValue experience) {
        if (money == null && experience == null) {
            return "No numeric values recognized; check calibration and scaling";
        }
        if (money == null) {
            return "Experience recognized; Money was not recognized";
        }
        if (experience == null) {
            return "Money recognized; Experience was not recognized";
        }
        return "Money and Experience recognized";
    }

    private void publish(GameDataSnapshot snapshot) {
        latestSnapshot = snapshot;
        for (Consumer<GameDataSnapshot> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException error) {
                LOGGER.log(Level.FINE, "Game data listener failed", error);
            }
        }
    }

    private void loadRegions() {
        for (GameDataField field : GameDataField.values()) {
            String serialized = preferences.get(regionKey(field), "");
            if (serialized.isBlank()) {
                continue;
            }
            try {
                regions.put(field, CaptureRegion.parse(serialized));
            } catch (RuntimeException error) {
                LOGGER.log(Level.FINE, "Ignoring invalid saved region for " + field, error);
            }
        }
    }

    private static String regionKey(GameDataField field) {
        return "region." + field.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static GameDataSnapshot copyWithStatus(GameDataSnapshot snapshot, String status) {
        return new GameDataSnapshot(
            snapshot.money(),
            snapshot.moneyChange(),
            snapshot.experience(),
            snapshot.sessionExperienceGained(),
            snapshot.rawMoneyText(),
            snapshot.rawExperienceText(),
            status,
            java.time.Instant.now()
        );
    }

    private static String message(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }
}
