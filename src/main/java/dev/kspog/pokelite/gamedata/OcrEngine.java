package dev.kspog.pokelite.gamedata;

import java.awt.image.BufferedImage;
import java.io.IOException;

public interface OcrEngine {
    String recognize(BufferedImage image, GameDataField field) throws IOException, InterruptedException;

    String describe();

    boolean isAvailable();
}
