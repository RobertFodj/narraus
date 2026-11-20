package com.narraus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Sovelluksen kuvakkeet: tiedostot ovat kelvollisia ja julkisia, mutta muut sivut pysyvät suojattuina. */
@NarrausTest
class IconTest {

    private static final String ROOT = "/META-INF/resources";

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private static byte[] read(String path) throws Exception {
        try (InputStream in = IconTest.class.getResourceAsStream(ROOT + path)) {
            assertNotNull(in, "Puuttuu: " + path);
            return in.readAllBytes();
        }
    }

    private static void assertValidSvg(String path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(IconTest.class.getResourceAsStream(ROOT + path));
        assertEquals("svg", doc.getDocumentElement().getNodeName(), path + " ei ole SVG");
        assertEquals("0 0 512 512", doc.getDocumentElement().getAttribute("viewBox"), path + ": viewBox puuttuu");
        assertTrue(doc.getElementsByTagName("title").getLength() > 0, path + ": otsikko (saavutettavuus) puuttuu");
    }

    private static void assertPng(String path, int size) throws Exception {
        var image = ImageIO.read(IconTest.class.getResourceAsStream(ROOT + path));
        assertNotNull(image, path + " ei ole kelvollinen PNG");
        assertEquals(size, image.getWidth(), path);
        assertEquals(size, image.getHeight(), path);
    }

    @Test
    void svgIcons_areValid() throws Exception {
        assertValidSvg("/icons/icon.svg");
        assertValidSvg("/icons/favicon.svg");
    }

    @Test
    void pwaSourceIcon_isLargeEnough_andHasOpaqueTopLeftPixelForTheSplashScreen() throws Exception {
        assertPng("/icons/icon.png", 512);
        var image = ImageIO.read(IconTest.class.getResourceAsStream(ROOT + "/icons/icon.png"));
        assertEquals(255, image.getRGB(0, 0) >>> 24,
                "iOS-aloituskuvan tausta otetaan vasemman yläkulman pikselistä: sen pitää olla peittävä");
    }

    @Test
    void favicon_isIcoWithThreeSizes() throws Exception {
        ByteBuffer ico = ByteBuffer.wrap(read("/favicon.ico")).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, ico.getShort(0), "ICO-otsikon varattu kenttä");
        assertEquals(1, ico.getShort(2), "Tyypin pitää olla 1 (ikoni)");
        assertEquals(3, ico.getShort(4), "Kuvakkeessa pitää olla kolme kokoa");
        int[] sizes = new int[3];
        for (int i = 0; i < 3; i++) sizes[i] = Byte.toUnsignedInt(ico.get(6 + i * 16));
        assertEquals(16, sizes[0]);
        assertEquals(32, sizes[1]);
        assertEquals(48, sizes[2]);
    }

    @Test
    void iconsArePublic_butOtherPagesStayProtected() throws Exception {
        // Vain Spring Securityn suodatinketju ilman todellista sivuhandleria:
        // läpi päässyt pyyntö päätyy tyhjään handleriin (404), estetty ohjataan tai hylätään (302/401/403)
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Object())
                .addFilters(springSecurityFilterChain).build();
        Set<Integer> denied = Set.of(302, 401, 403);

        for (String path : new String[] {"/icons/favicon.svg", "/icons/icon.svg", "/icons/icon.png",
                "/icons/icon-192x192.png", "/favicon.ico", "/manifest.webmanifest", "/sw.js", "/offline.html"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertFalse(denied.contains(status), path + " pitää olla julkinen (kirjautumissivu ja asennus tarvitsevat sen), tila " + status);
        }
        for (String path : new String[] {"/users", "/people", "/profile", "/chat", "/map"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            assertTrue(denied.contains(status), path + " ei saa olla julkinen, tila " + status);
        }
    }
}
