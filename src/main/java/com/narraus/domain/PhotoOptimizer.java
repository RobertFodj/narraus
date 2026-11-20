package com.narraus.domain;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Pienentää kuvia, jotta niiden lataaminen on nopeaa: skaalaa pitkän sivun enintään annettuun kokoon ja
 * pakkaa JPEG:ksi. Puhelinkuvien EXIF-kääntötieto huomioidaan, koska uudelleenkoodattu kuva ei sisällä sitä
 * ja pystykuvat kääntyisivät muuten kyljelleen.
 */
final class PhotoOptimizer {

    private PhotoOptimizer() {}

    /** Pienennetty JPEG, tai tyhjä jos kuvaa ei voi lukea (esim. webp). */
    static Optional<byte[]> shrink(byte[] data, int maxEdge, float quality) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            if (src == null) return Optional.empty();
            int orientation = orientation(data);
            BufferedImage scaled = scale(src, maxEdge);
            BufferedImage oriented = orient(scaled, orientation);
            return Optional.of(encodeJpeg(flatten(oriented), quality));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Kuvan pitkä sivu pikseleinä, tai -1 jos kuvaa ei voi lukea. */
    static int longEdge(byte[] data) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(data));
            return src == null ? -1 : Math.max(src.getWidth(), src.getHeight());
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    private static BufferedImage scale(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int edge = Math.max(w, h);
        if (edge <= maxEdge) return src;
        double ratio = (double) maxEdge / edge;
        int tw = Math.max(1, (int) Math.round(w * ratio));
        int th = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage current = src;
        // Puolitetaan vaiheittain, jotta pienennys pysyy terävänä
        while (current.getWidth() / 2 >= tw && current.getHeight() / 2 >= th) {
            current = draw(current, current.getWidth() / 2, current.getHeight() / 2);
        }
        return draw(current, tw, th);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Läpinäkyvyys valkoiselle pohjalle, koska JPEG ei tue sitä. */
    private static BufferedImage flatten(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    // ---- EXIF-kääntötieto ----

    /** EXIF Orientation (1-8); 1 jos tietoa ei ole tai kuva ei ole JPEG. */
    static int orientation(byte[] jpeg) {
        try {
            if (jpeg.length < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) return 1;
            int pos = 2;
            while (pos + 4 <= jpeg.length && u8(jpeg, pos) == 0xFF) {
                int marker = u8(jpeg, pos + 1);
                if (marker == 0xDA || marker == 0xD9) break;
                int len = (u8(jpeg, pos + 2) << 8) | u8(jpeg, pos + 3);
                if (marker == 0xE1 && len >= 14 && jpeg[pos + 4] == 'E' && jpeg[pos + 5] == 'x'
                        && jpeg[pos + 6] == 'i' && jpeg[pos + 7] == 'f') {
                    return exifOrientation(jpeg, pos + 10);
                }
                pos += 2 + len;
            }
        } catch (RuntimeException ignored) {
            // vioittunut EXIF: käsitellään kuin tietoa ei olisi
        }
        return 1;
    }

    private static int exifOrientation(byte[] b, int tiff) {
        boolean little = b[tiff] == 'I';
        int ifd = tiff + (int) read(b, tiff + 4, 4, little);
        int entries = (int) read(b, ifd, 2, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + 12 * i;
            if (read(b, entry, 2, little) == 0x0112) {
                int value = (int) read(b, entry + 8, 2, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static long read(byte[] b, int pos, int bytes, boolean little) {
        long v = 0;
        for (int i = 0; i < bytes; i++) {
            int octet = u8(b, little ? pos + bytes - 1 - i : pos + i);
            v = (v << 8) | octet;
        }
        return v;
    }

    /** Kääntää/peilaa kuvan EXIF-arvon mukaan niin, että se näkyy oikein päin ilman EXIF-tietoa. */
    static BufferedImage orient(BufferedImage src, int orientation) {
        if (orientation < 2 || orientation > 8) return src;
        int w = src.getWidth();
        int h = src.getHeight();
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx;
                int dy;
                switch (orientation) {
                    case 2 -> { dx = w - 1 - x; dy = y; }
                    case 3 -> { dx = w - 1 - x; dy = h - 1 - y; }
                    case 4 -> { dx = x; dy = h - 1 - y; }
                    case 5 -> { dx = y; dy = x; }
                    case 6 -> { dx = h - 1 - y; dy = x; }
                    case 7 -> { dx = h - 1 - y; dy = w - 1 - x; }
                    default -> { dx = y; dy = w - 1 - x; }
                }
                out.setRGB(dx, dy, src.getRGB(x, y));
            }
        }
        return out;
    }
}
