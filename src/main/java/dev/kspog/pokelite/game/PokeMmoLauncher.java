package dev.kspog.pokelite.game;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PokeMmoLauncher implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(PokeMmoLauncher.class.getName());

    private final Path installationDirectory;
    private final ExecutorService outputReader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pokemmo-output-reader");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Process process;

    public PokeMmoLauncher(Path installationDirectory) {
        this.installationDirectory = Objects.requireNonNull(installationDirectory)
            .toAbsolutePath()
            .normalize();
    }

    public Path getInstallationDirectory() {
        return installationDirectory;
    }

    public synchronized Process launch() throws IOException {
        if (isRunning()) {
            return process;
        }

        Path executable = resolveExecutable();
        ProcessBuilder builder;

        if (executable.getFileName().toString().endsWith(".sh")) {
            builder = new ProcessBuilder("bash", executable.toString());
        } else {
            builder = new ProcessBuilder(executable.toString());
        }

        builder.directory(installationDirectory.toFile());
        builder.redirectErrorStream(true);

        LOGGER.info(() -> "Launching PokeMMO from " + executable);
        process = builder.start();
        readOutput(process);
        return process;
    }

    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    public synchronized Process getProcess() {
        return process;
    }

    public synchronized void stop() {
        Process current = process;
        if (current == null || !current.isAlive()) {
            return;
        }

        LOGGER.info("Stopping PokeMMO process");
        current.destroy();
    }

    private Path resolveExecutable() throws IOException {
        boolean windows = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");

        Path executable = installationDirectory.resolve(windows ? "PokeMMO.exe" : "PokeMMO.sh");
        if (!Files.isRegularFile(executable)) {
            throw new IOException("PokeMMO executable was not found: " + executable);
        }
        return executable;
    }

    private void readOutput(Process launchedProcess) {
        outputReader.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                launchedProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[PokeMMO] " + line);
                }
            } catch (IOException error) {
                if (launchedProcess.isAlive()) {
                    LOGGER.log(Level.WARNING, "Unable to read PokeMMO output", error);
                }
            }
        });
    }

    @Override
    public void close() {
        stop();
        outputReader.shutdownNow();
    }
}
