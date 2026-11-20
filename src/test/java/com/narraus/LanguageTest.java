package com.narraus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import tools.jackson.databind.ObjectMapper;
import com.narraus.ui.Language;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Kielituki: neljä kieltä, suomi lähdekielenä; muiden kielten sanakirjat ladataan selaimeen vain tarvittaessa. */
@NarrausTest
class LanguageTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @SuppressWarnings("unchecked")
    private static Map<String, String> dictionary(String code) throws Exception {
        try (InputStream in = LanguageTest.class.getResourceAsStream("/META-INF/resources/i18n/" + code + ".json")) {
            return new ObjectMapper().readValue(in, LinkedHashMap.class);
        }
    }

    private static List<String> placeholders(String s) {
        Matcher m = PLACEHOLDER.matcher(s);
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) out.add(m.group());
        return out.stream().sorted().toList();
    }

    @Test
    void languages_areFinnishSwedishNorwegianAndEnglish() {
        assertEquals(List.of("Suomi", "Svenska", "Norsk", "English"),
                java.util.Arrays.stream(Language.values()).map(Language::nativeName).toList());
        assertEquals(Language.FI, Language.fromCookie(null), "Ensimmäisellä kerralla suomi");
        assertEquals(Language.FI, Language.fromCookie("xx"), "Tuntematon kieli on suomi");
        assertEquals(Language.NO, Language.fromCookie("no"));
        assertEquals("nb", Language.NO.locale().getLanguage());
    }

    @Test
    void dictionaries_haveTheSameKeys_andPlaceholders_inAllThreeLanguages() throws Exception {
        var sv = dictionary("sv");
        var no = dictionary("no");
        var en = dictionary("en");

        assertTrue(sv.size() > 250, "Sanakirja on kattava");
        assertEquals(sv.keySet(), no.keySet());
        assertEquals(sv.keySet(), en.keySet());
        for (String key : sv.keySet()) {
            for (var dict : List.of(sv, no, en)) {
                String value = dict.get(key);
                assertFalse(value == null || value.isBlank(), "Käännös puuttuu: " + key);
                assertEquals(placeholders(key), placeholders(value), "Paikkamerkit eivät täsmää: " + key);
            }
        }
    }

    @Test
    void dictionaries_translateTheMainNavigationAndAccountTexts() throws Exception {
        for (String key : List.of("Saaliit", "Käyttäjät", "Keskustelut", "Kilpailut", "Oma tili", "Kirjaudu ulos",
                "Kirjaudu sisään", "Ilmoitukset", "Omat julkaisut", "Seuraajat", "Tilin muokkaus", "Poista tili",
                "Peruuta", "Tallenna", "Ymmärrän", "Kieli")) {
            for (String code : List.of("sv", "no", "en")) {
                String t = dictionary(code).get(key);
                assertTrue(t != null && !t.isBlank(), code + " puuttuu: " + key);
                assertNotEquals(key, t, code + ": käännös on sama kuin suomi: " + key);
            }
        }
        assertEquals("Mitt konto", dictionary("sv").get("Oma tili"));
        assertEquals("Min konto", dictionary("no").get("Oma tili"));
        assertEquals("My account", dictionary("en").get("Oma tili"));
    }

    @Test
    void dictionaries_areServedWithoutLogin_soTheLoginPageCanSwitchLanguage() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Object()).apply(springSecurity(springSecurityFilterChain)).build();

        for (String code : List.of("sv", "no", "en")) {
            MockHttpServletResponse response = mvc.perform(get("/i18n/" + code + ".json")).andReturn().getResponse();
            // Pelkässä suodatinketjutestissä ei ole staattisten tiedostojen käsittelijää (404), mutta suojaus ei saa estää (403/302)
            assertTrue(response.getStatus() == 200 || response.getStatus() == 404,
                    "/i18n/" + code + ".json pitää olla julkinen, mutta tila oli " + response.getStatus());
        }
    }

    @Test
    void translatorScript_isInTheAppShell_andKnowsTheFourLanguages() throws Exception {
        try (InputStream in = LanguageTest.class.getResourceAsStream("/META-INF/resources/narraus-i18n.js")) {
            String js = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (Language l : Language.values()) assertTrue(js.contains("'" + l.code() + "'"), l.code());
            assertTrue(js.contains("narraus-lang"), "Eväste, johon vain valittu kieli tallennetaan");
            assertTrue(js.contains("narrausLang"));
        }
    }

    @Test
    void translatorKeepsTheIconInFrontOfTranslatedText_forCompetitionLines() throws Exception {
        try (InputStream in = LanguageTest.class.getResourceAsStream("/META-INF/resources/narraus-i18n.js")) {
            String js = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(js.contains("var iconic ="), "Kuvakkeella alkavat rivit (🏆 Pisin kala) käännetään kuvake säilyttäen");
        }
        for (String code : List.of("sv", "no", "en")) {
            var dict = dictionary(code);
            for (String key : List.of("Suurin paino", "Pisin kala", "Suurin ympärysmitta", "Eniten kaloja",
                    "Voittaa se, jolla on painavin kala.", "Voittaa se, jolla on pisin kala.",
                    "Voittaa se, jolla on suurin ympärysmitta.", "Voittaa se, jolla on eniten kaloja.")) {
                assertTrue(dict.containsKey(key), code + " puuttuu: " + key);
            }
        }
    }
}
