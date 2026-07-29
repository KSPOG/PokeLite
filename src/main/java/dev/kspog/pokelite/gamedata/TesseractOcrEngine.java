package dev.kspog.pokelite.gamedata;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TesseractOcrEngine implements OcrEngine {
    private static final long TIMEOUT_SECONDS = 10L;

    private final String executable;

    public TesseractOcrEngine(String executable) {
        this.executable = executable == null || executable.isBlank()
            ? defaultExecutable()
            : executable.trim();
    }

    public String getExecutable() {
        return executable;
    }

    @Override
    public String recognize(BufferedImage image, GameDataField field)
        throws IOException, InterruptedException {
        Path input = Files.createTempFile("pokelite-ocr-", ".png");
        try {
            ImageIO.write(ImagePreprocessor.prepareForOcr(image), "png", input.toFile());

            List<String> command = new ArrayList<>();
            command.add(executable);
            command.add(input.toString());
            command.add("stdout");
            command.add("--psm");
            command.add("7");
            command.add("-l");
            command.add("eng");
            command.add("-c");
            command.add(field == GameDataField.MONEY
                ? "tessedit_char_whitelist=0123456789,.$"
                : "tessedit_char_whitelist=0123456789,+/EXPexp ");

            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Tesseract timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Tesseract exited with code " + process.exitValue() + ": " + output.trim());
            }
            return output.trim();
        } finally {
            Files.deleteIfExists(input);
        }
    }

    @Override
    public String describe() {
        return executable;
    }

    @Override
    public boolean isAvailable() {
        if (looksLikePath(executable)) {
            return Files.isRegularFile(Path.of(executable));
        }

        try {
            Process process = new ProcessBuilder(executable, "--version")
                .redirectErrorStream(true)
                .start();
            return process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static String defaultExecutable() {
        if (!isWindows()) {
            return "tesseract";
        }

        List<Path> candidates = List.of(
            Path.of("C:\\Program Files\\Tesseract-OCR\\tesseract.exe"),
            Path.of("C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe")
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toString();
            }
        }
        return "tesseract";
    }

    private static boolean looksLikePath(String value) {
        return value.contains("\\") || value.contains("/") || value.toLowerCase(Locale.ROOT).endsWith(".exe");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");
    }
}
