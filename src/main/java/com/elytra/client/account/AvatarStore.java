package com.elytra.client.account;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Turns whatever image the user picked into a small square PNG.
 *
 * The original file is never copied: it is decoded with size limits, centre-cropped,
 * scaled to {@link #SIZE} and re-encoded, so only clean pixel data ends up on disk.
 */
public final class AvatarStore {

    public static final int SIZE = 128;
    private static final long MAX_FILE_BYTES = 12L * 1024 * 1024;
    private static final long MAX_PIXELS = 40_000_000L;

    private AvatarStore() {
    }

    /** Reads, validates and converts an image file to a SIZE x SIZE ARGB square. */
    public static BufferedImage readSquare(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("That file does not exist.");
        }
        if (Files.size(file) > MAX_FILE_BYTES) {
            throw new IOException("Image is larger than 12 MB.");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                throw new IOException("Could not open that file.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("That is not a supported image (use PNG, JPG, GIF or BMP).");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width * height > MAX_PIXELS) {
                    throw new IOException("Image dimensions are too large.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("Could not decode that image.");
                }
                return toSquare(image);
            } finally {
                reader.dispose();
            }
        }
    }

    static BufferedImage toSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage current = copyArgb(source.getSubimage(
                (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side));
        int size = side;
        // Halve repeatedly so big photos are averaged instead of aliased.
        while (size / 2 >= SIZE) {
            size /= 2;
            current = scale(current, size);
        }
        return size == SIZE ? current : scale(current, SIZE);
    }

    private static BufferedImage copyArgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, size, size, null);
        graphics.dispose();
        return out;
    }

    static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(16 * 1024);
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("PNG encoder unavailable.");
        }
        return bytes.toByteArray();
    }
}
