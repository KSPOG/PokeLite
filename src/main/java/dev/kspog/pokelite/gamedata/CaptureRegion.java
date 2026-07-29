package dev.kspog.pokelite.gamedata;

import java.awt.Rectangle;

public record CaptureRegion(double x, double y, double width, double height) {
    public CaptureRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0
            || x + width > 1.000001 || y + height > 1.000001) {
            throw new IllegalArgumentException("Capture region must be normalized inside the game canvas");
        }
    }

    public static CaptureRegion fromPixels(Rectangle rectangle, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive");
        }
        return new CaptureRegion(
            rectangle.x / (double) imageWidth,
            rectangle.y / (double) imageHeight,
            rectangle.width / (double) imageWidth,
            rectangle.height / (double) imageHeight
        );
    }

    public Rectangle toPixels(int imageWidth, int imageHeight) {
        int px = clamp((int) Math.round(x * imageWidth), 0, Math.max(0, imageWidth - 1));
        int py = clamp((int) Math.round(y * imageHeight), 0, Math.max(0, imageHeight - 1));
        int pw = clamp((int) Math.round(width * imageWidth), 1, Math.max(1, imageWidth - px));
        int ph = clamp((int) Math.round(height * imageHeight), 1, Math.max(1, imageHeight - py));
        return new Rectangle(px, py, pw, ph);
    }

    public String serialize() {
        return x + "," + y + "," + width + "," + height;
    }

    public static CaptureRegion parse(String value) {
        String[] parts = value.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid capture region");
        }
        return new CaptureRegion(
            Double.parseDouble(parts[0]),
            Double.parseDouble(parts[1]),
            Double.parseDouble(parts[2]),
            Double.parseDouble(parts[3])
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
