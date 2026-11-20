package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.narraus.NarrausTest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@NarrausTest
class PhotoOptimizerTest {

    @Autowired
    private UserService users;
    @Autowired
    private PostService posts;
    @Value("${narraus.upload-dir}")
    private String uploadDir;

    private AppUser newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, "Kuvaaja-" + id, "salasana123");
    }

    private static byte[] jpeg(int w, int h, boolean noisy) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(1);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, noisy ? rnd.nextInt(0xFFFFFF) : 0x336699);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    /** JPEG, jonka alkuun on lisätty EXIF-tieto kääntösuunnasta (big endian). */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {'M', 'M', 0, 0x2A, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
                0, 0, 0, 0};
        int segLen = 2 + 6 + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);
        out.write(segLen >> 8);
        out.write(segLen & 0xFF);
        out.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2); // alkuperäinen ilman SOI-merkkiä
        return out.toByteArray();
    }

    @Test
    void largePhoto_isStoredSmaller_withLimitedDimensions() throws IOException {
        byte[] big = jpeg(3200, 2000, true);
        CatchPost post = posts.create(newUser(), "Hauki", null, null, null, null, null, big, "image/jpeg");

        byte[] stored = Files.readAllBytes(Path.of(uploadDir).resolve(post.getPhotoFile()));
        BufferedImage img = decode(stored);
        assertTrue(stored.length < big.length, "Tallennettu kuva on pienempi: " + stored.length + " < " + big.length);
        assertEquals(1600, Math.max(img.getWidth(), img.getHeight()), "Pisin sivu rajataan");
        assertEquals(1600.0 / 3200, img.getWidth() / 3200.0, 0.001, "Mittasuhteet säilyvät");
        assertTrue(post.getPhotoFile().endsWith(".jpg"));
    }

    @Test
    void smallPhoto_isLeftAsItIs() throws IOException {
        byte[] small = jpeg(300, 200, false);
        assertTrue(small.length < 300_000);
        CatchPost post = posts.create(newUser(), "Ahven", null, null, null, null, null, small, "image/jpeg");

        assertArrayEquals(small, Files.readAllBytes(Path.of(uploadDir).resolve(post.getPhotoFile())));
    }

    @Test
    void unreadableImage_isStoredUnchanged() throws IOException {
        byte[] fake = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        CatchPost post = posts.create(newUser(), "Kuha", null, null, null, null, null, fake, "image/png");

        assertArrayEquals(fake, Files.readAllBytes(Path.of(uploadDir).resolve(post.getPhotoFile())));
        try (var in = posts.openThumbnail(post.getPhotoFile())) {
            assertArrayEquals(fake, in.readAllBytes(), "Esikatselun sijasta palautuu alkuperäinen, ei virhettä");
        }
    }

    @Test
    void thumbnail_isSmall_createdOnce_andRemovedWithThePost() throws IOException {
        AppUser me = newUser();
        byte[] big = jpeg(3200, 2000, true);
        CatchPost post = posts.create(me, "Hauki", null, null, null, null, null, big, "image/jpeg");
        String file = post.getPhotoFile();
        String thumbName = file.substring(0, file.lastIndexOf('.')) + "-thumb.jpg";
        Path thumbPath = Path.of(uploadDir).resolve(thumbName);
        assertFalse(Files.exists(thumbPath), "Esikatselu tehdään vasta tarvittaessa");

        byte[] thumb;
        try (var in = posts.openThumbnail(file)) {
            thumb = in.readAllBytes();
        }
        BufferedImage img = decode(thumb);
        assertEquals(480, Math.max(img.getWidth(), img.getHeight()));
        assertTrue(thumb.length < Files.size(Path.of(uploadDir).resolve(file)) / 3, "Esikatselu on selvästi pienempi");
        assertTrue(Files.exists(thumbPath), "Esikatselu tallennetaan levylle");
        try (var in = posts.openThumbnail(file)) {
            assertArrayEquals(thumb, in.readAllBytes(), "Toisella kerralla käytetään tallennettua");
        }

        posts.delete(post.getId(), me);
        assertFalse(Files.exists(Path.of(uploadDir).resolve(file)));
        assertFalse(Files.exists(thumbPath), "Esikatselu poistuu julkaisun mukana");
    }

    @Test
    void phonePortraitPhoto_keepsItsOrientation() throws IOException {
        // 200x100 -kuva, jonka vasen yläkulma on punainen; EXIF 6 = kuva on näytettävä 90° myötäpäivään
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        var g = src.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 200, 100);
        g.setColor(Color.RED);
        g.fillRect(0, 0, 30, 30);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(src, "jpg", out);
        byte[] tagged = withOrientation(out.toByteArray(), 6);
        assertEquals(6, PhotoOptimizer.orientation(tagged));

        byte[] result = PhotoOptimizer.shrink(tagged, 1600, 0.9f).orElseThrow();
        BufferedImage img = decode(result);

        assertNotNull(img);
        assertEquals(100, img.getWidth(), "Leveä kuva kääntyy pystyyn");
        assertEquals(200, img.getHeight());
        Color topRight = new Color(img.getRGB(90, 10));
        assertTrue(topRight.getRed() > 150 && topRight.getBlue() < 120, "Punainen kulma on nyt oikeassa yläkulmassa");
    }

    @Test
    void orientationIsOne_withoutExif() throws IOException {
        assertEquals(1, PhotoOptimizer.orientation(jpeg(50, 50, false)));
        assertEquals(1, PhotoOptimizer.orientation(new byte[]{1, 2, 3}));
    }
}
