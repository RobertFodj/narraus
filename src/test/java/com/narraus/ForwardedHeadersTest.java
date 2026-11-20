package com.narraus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Tuotannossa sovellus on Caddyn takana. Jos X-Forwarded-*-otsikoihin ei luoteta, sovellus luulee pyyntöä
 * http-pyynnöksi: evästeet jäävät ilman Secure-lippua (myös pysyvä kirjautuminen) ja ohjaukset alkavat http://.
 */
@NarrausTest
class ForwardedHeadersTest {

    @Autowired
    private Environment env;

    @Test
    void forwardedHeaders_areTrusted_soTheAppKnowsItIsServedOverHttps() {
        assertEquals("native", env.getProperty("server.forward-headers-strategy"),
                "Spring Bootin oikea asetus on server.forward-headers-strategy (ei ...forwarded...)");
    }

    @Test
    void theConfigurationIsInTheCommittedProperties_notOnlyInTheServerEnvironment() throws Exception {
        try (InputStream in = ForwardedHeadersTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(in);
            String props = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(props.lines().anyMatch(l -> l.strip().equals("server.forward-headers-strategy=native")),
                    "Asetuksen pitää olla application.propertiesissa, jotta se on mukana jokaisessa julkaisussa");
        }
    }
}
