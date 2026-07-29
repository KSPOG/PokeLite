package dev.kspog.pokelite.gamedata;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class ImagePreprocessor {
    private static final int SCALE = 3;

    private ImagePreprocessor() {
    }

    public static BufferedImage prepareForOcr(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(
            Math.max(1, source.getWidth() * SCALE),
            Math.max(1, source.getHeight() * SCALE),
            BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.drawImage(source, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
        } finally {
            graphics.dispose();
        }

        long total = 0;
        int count = scaled.getWidth() * scaled.getHeight();
        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                total += luminance(scaled.getRGB(x, y));
            }
        }

        int average = count == 0 ? 128 : (int) (total / count);
        boolean darkBackground = average < 128;
        int threshold = darkBackground
            ? Math.min(235, average + 35)
            : Math.max(20, average - 35);

        BufferedImage output = new BufferedImage(
            scaled.getWidth(),
            scaled.getHeight(),
            BufferedImage.TYPE_BYTE_BINARY
        );

        for (int y = 0; y < scaled.getHeight(); y++) {
            for (int x = 0; x < scaled.getWidth(); x++) {
                int brightness = luminance(scaled.getRGB(x, y));
                boolean foreground = darkBackground
                    ? brightness >= threshold
                    : brightness <= threshold;
                output.setRGB(x, y, foreground ? 0xFF000000 : 0xFFFFFFFF);
            }
        }

        return output;
    }

    private static int luminance(int rgb) {
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }
}
