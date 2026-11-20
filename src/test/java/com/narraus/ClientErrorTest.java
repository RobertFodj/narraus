package com.narraus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Käynnistysvahti: jos sovellus ei piirry selaimessa (esim. liian vanha selain), selain näyttää ohjeen ja ilmoittaa
 * syyn palvelimelle ilman kirjautumista.
 */
@NarrausTest
class ClientErrorTest {

    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    @Autowired
    private ClientErrorController controller;

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).apply(springSecurity(springSecurityFilterChain)).build();
    }

    @Test
    void report_isAcceptedWithoutLoginOrCsrfToken() throws Exception {
        int status = mvc().perform(post("/client-error").contentType(MediaType.TEXT_PLAIN)
                .content("{\"ua\":\"Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X)\",\"features\":\"popover=false\"}"))
                .andReturn().getResponse().getStatus();

        assertEquals(204, status, "Selain voi ilmoittaa virheestä ennen kirjautumista");
    }

    @Test
    void emptyReport_isAcceptedToo() throws Exception {
        assertEquals(204, mvc().perform(post("/client-error")).andReturn().getResponse().getStatus());
    }

    @Test
    void loggedText_isOneLine_andShort() {
        String cleaned = ClientErrorController.clean("a\nb\r\nc\td" + "x".repeat(5000));

        assertFalse(cleaned.contains("\n") || cleaned.contains("\r") || cleaned.contains("\t"),
                "Rivinvaihdoilla ei saa väärentää lokia");
        assertTrue(cleaned.length() <= 1501);
        assertEquals("", ClientErrorController.clean(null));
    }

    @Test
    void bootCheckScript_hasTheMessageInAllFourLanguages_andIsPlainEs5() throws Exception {
        try (InputStream in = ClientErrorTest.class.getResourceAsStream("/META-INF/resources/narraus-boot-check.js")) {
            String js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String text : new String[]{"Narrauksen avaaminen ei onnistunut", "Det gick inte att öppna Narraus",
                    "Kunne ikke åpne Narraus", "Narraus could not be opened"}) {
                assertTrue(js.contains(text), text);
            }
            assertTrue(js.contains("/client-error"), "Ilmoitus lähetetään palvelimelle");
            assertTrue(js.contains("iOS 17"), "Kertoo iPhonen vaatiman version");
            // Vanhoissa selaimissa vahdin pitää toimia: ei nuolifunktioita, let/const-sanoja eikä optional chainingia
            assertFalse(js.contains("=>") || js.contains("?.") || js.contains("let ") || js.contains("const "),
                    "Vahti on kirjoitettu ES5:llä");
        }
    }
}
