package com.narraus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.narraus.domain.AccountService;
import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * "Pysy kirjautuneena": kirjautumisen jälkeen selain pääsee sisään ilman uutta kirjautumista, kunnes
 * uloskirjaudutaan tai tili poistetaan. Testit ajavat oikean Spring Securityn suodatinketjun ja tietokannan.
 *
 * <p>Kirjautumisen onnistuminen luetaan istunnon suojauskontekstista (kenet Spring tunnisti), ei HTTP-tilasta:
 * se, onko osoite Vaadinin sivu, riippuu Vaadinin reittirekisteristä, jota pelkässä suodatinketjutestissä ei ole.
 */
@NarrausTest
class RememberMeTest {

    private static final String COOKIE = "remember-me";
    private static final String PASSWORD = "salasana123";

    @Autowired
    private FilterChainProxy springSecurityFilterChain;
    @Autowired
    private UserService users;
    @Autowired
    private AccountService accounts;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RememberMeCleanup cleanup;
    @Value("${narraus.remember-me-days}")
    private int days;

    /** Yksi selainvierailu: palvelimen vastaus ja se, kenet Spring tunnisti pyynnölle (tai null). */
    private record Visit(MockHttpServletResponse response, String username) {
        Cookie cookie() {
            return response.getCookie(COOKIE);
        }

        boolean cookieCancelled() {
            Cookie c = cookie();
            return c != null && c.getMaxAge() == 0;
        }
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new Object()).apply(springSecurity(springSecurityFilterChain)).build();
    }

    private AppUser newUser() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return users.register("u-" + id, "Käyttäjä-" + id, PASSWORD);
    }

    private MockHttpServletResponse login(String username, String password, boolean secure) throws Exception {
        return mvc().perform(post("/login").param("username", username).param("password", password).with(csrf())
                .secure(secure)).andReturn().getResponse();
    }

    private Cookie loginCookie(AppUser user) throws Exception {
        Cookie cookie = login(user.getUsername(), PASSWORD, false).getCookie(COOKIE);
        assertNotNull(cookie, "Kirjautuminen pitää antaa pysyvä eväste");
        return cookie;
    }

    /** Uusi selain (ei istuntoa) tuo vain evästeen palvelimelle. */
    private Visit visit(Cookie cookie) throws Exception {
        var request = get("/");
        if (cookie != null) request = request.cookie(cookie);
        MvcResult result = mvc().perform(request).andReturn();
        String username = null;
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context instanceof SecurityContext sc && sc.getAuthentication() != null) {
                username = sc.getAuthentication().getName();
            }
        }
        return new Visit(result.getResponse(), username);
    }

    private boolean isLoggedInAs(Cookie cookie, AppUser user) throws Exception {
        return user.getUsername().equals(visit(cookie).username());
    }

    private boolean isLoggedOut(Cookie cookie) throws Exception {
        return visit(cookie).username() == null;
    }

    private long tokenRows(AppUser user) {
        return jdbc.queryForObject("select count(*) from persistent_logins where username = ?", Long.class,
                user.getUsername());
    }

    private String tokenValue(AppUser user) {
        return jdbc.queryForObject("select token from persistent_logins where username = ?", String.class,
                user.getUsername());
    }

    private void setLastUsed(AppUser user, Instant when) {
        jdbc.update("update persistent_logins set last_used = ? where username = ?", Timestamp.from(when),
                user.getUsername());
    }

    private Instant lastUsed(AppUser user) {
        return jdbc.queryForObject("select last_used from persistent_logins where username = ?", Timestamp.class,
                user.getUsername()).toInstant();
    }

    // ---- eväste kirjautumisessa ----

    @Test
    void withoutACookie_nobodyIsLoggedIn() throws Exception {
        assertNull(visit(null).username(), "Perusoletus: kirjautumaton pysyy kirjautumattomana");
    }

    @Test
    void logIn_givesALongLivedHttpOnlyCookie_andStoresTheToken() throws Exception {
        AppUser me = newUser();

        MockHttpServletResponse response = login(me.getUsername(), PASSWORD, false);

        assertEquals(302, response.getStatus());
        assertFalse(String.valueOf(response.getRedirectedUrl()).contains("error"), "Kirjautuminen onnistui");
        Cookie cookie = response.getCookie(COOKIE);
        assertNotNull(cookie, "Eväste annetaan ilman valintaruutua (aina päällä)");
        assertEquals(days * 24 * 60 * 60, cookie.getMaxAge(), "Eväste kestää asetetut päivät");
        assertTrue(cookie.isHttpOnly(), "Skriptit eivät saa lukea evästettä");
        assertEquals(1, tokenRows(me), "Tunniste on tallennettu tietokantaan");
    }

    @Test
    void cookieIsSecure_whenTheConnectionIsHttps() throws Exception {
        AppUser me = newUser();

        Cookie cookie = login(me.getUsername(), PASSWORD, true).getCookie(COOKIE);

        assertNotNull(cookie);
        assertTrue(cookie.getSecure(), "HTTPS-yhteydellä eväste kulkee vain salattuna");
    }

    @Test
    void wrongPassword_givesNoCookie_andNoToken() throws Exception {
        AppUser me = newUser();

        MockHttpServletResponse response = login(me.getUsername(), "vaara-salasana", false);

        Cookie cookie = response.getCookie(COOKIE);
        assertTrue(cookie == null || cookie.getMaxAge() == 0, "Epäonnistunut kirjautuminen ei saa antaa evästettä");
        assertTrue(String.valueOf(response.getRedirectedUrl()).contains("error"));
        assertEquals(0, tokenRows(me));
    }

    @Test
    void theCookieHoldsOnlyARandomSeriesAndToken_neverThePasswordOrTheUsername() throws Exception {
        AppUser me = newUser();

        Cookie cookie = loginCookie(me);

        String decoded = new String(Base64.getDecoder().decode(java.net.URLDecoder.decode(cookie.getValue(),
                java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(2, decoded.split(":").length, "Vain sarja ja tunniste");
        assertFalse(decoded.contains(PASSWORD), "Eväste ei sisällä salasanaa");
        assertFalse(decoded.contains(me.getUsername()), "Eikä edes tunnusta");
    }

    // ---- sisäänpääsy evästeellä ----

    @Test
    void anotherBrowserSession_withTheCookie_isLoggedInWithoutLoggingInAgain() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);

        Visit visit = visit(cookie);

        assertEquals(me.getUsername(), visit.username(), "Eväste riittää: ei uutta kirjautumista");
    }

    @Test
    void theSameCookie_worksRepeatedlyAndInParallel_withoutBeingTreatedAsTheft() throws Exception {
        // Vaadin tekee sivua avatessa useita rinnakkaisia pyyntöjä samalla evästeellä. Jokaisen pitää onnistua,
        // eikä yksikään saa kumota tunnisteita tai kaataa pyyntöä virheeseen.
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);
        String tokenBefore = tokenValue(me);

        for (int i = 0; i < 5; i++) {
            assertTrue(isLoggedInAs(cookie, me), "Käyttökerta " + (i + 1));
        }

        assertEquals(1, tokenRows(me), "Rivejä ei kerry");
        assertEquals(tokenBefore, tokenValue(me), "Tunnistetta ei kierrätetä, joten rinnakkaiset pyynnöt eivät kilpaile");
    }

    @Test
    void usingTheCookie_doesNotNeedANewCookie_whenItWasUsedRecently() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);

        Visit visit = visit(cookie);

        assertEquals(me.getUsername(), visit.username());
        assertNull(visit.cookie(), "Tuoreena käytettyä evästettä ei uusita joka pyynnöllä");
    }

    @Test
    void usingAnOldCookie_slidesTheExpiryForward() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);
        setLastUsed(me, Instant.now().minus(2, ChronoUnit.DAYS));

        Visit visit = visit(cookie);

        assertEquals(me.getUsername(), visit.username());
        Cookie refreshed = visit.cookie();
        assertNotNull(refreshed, "Yli vuorokauden vanha eväste uusitaan käytössä");
        assertEquals(cookie.getValue(), refreshed.getValue(), "Sama tunniste, vain voimassaolo pitenee");
        assertEquals(days * 24 * 60 * 60, refreshed.getMaxAge());
        assertTrue(lastUsed(me).isAfter(Instant.now().minus(1, ChronoUnit.HOURS)), "Käyttöaika päivittyy");
    }

    @Test
    void aCookieUnusedForLongerThanTheValidity_isRejectedAndItsTokenRemoved() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);
        setLastUsed(me, Instant.now().minus(days + 1L, ChronoUnit.DAYS));

        Visit visit = visit(cookie);

        assertNull(visit.username(), "Vanhentunut eväste ei päästä sisään");
        assertTrue(visit.cookieCancelled(), "Selaimen vanhentunut eväste tyhjennetään");
        assertEquals(0, tokenRows(me), "Vanhentunut tunniste poistetaan");
    }

    @Test
    void aWrongTokenWithAValidSeries_isRejected_butDoesNotLogTheUsersOtherDevicesOut() throws Exception {
        // Muuten sarjan tunteva hyökkääjä voisi kirjata käyttäjän toistuvasti ulos kaikilta laitteilta
        AppUser me = newUser();
        Cookie phone = loginCookie(me);
        Cookie laptop = loginCookie(me);
        String series = new String(Base64.getDecoder().decode(java.net.URLDecoder.decode(phone.getValue(),
                java.nio.charset.StandardCharsets.UTF_8))).split(":")[0];
        Cookie forged = new Cookie(COOKIE, Base64.getEncoder().encodeToString((series + ":vaara").getBytes()));

        Visit forgedVisit = visit(forged);

        assertNull(forgedVisit.username(), "Väärä tunniste hylätään");
        assertTrue(forgedVisit.cookieCancelled());
        assertEquals(2, tokenRows(me), "Muita tunnisteita ei kumota");
        assertTrue(isLoggedInAs(phone, me), "Oikea puhelin toimii yhä");
        assertTrue(isLoggedInAs(laptop, me), "Kannettava toimii yhä");
    }

    @Test
    void aTamperedOrGarbageCookie_isRejected() throws Exception {
        AppUser me = newUser();
        loginCookie(me);

        String fake = Base64.getEncoder().encodeToString("keksitty-sarja:keksitty-tunniste".getBytes());
        assertTrue(isLoggedOut(new Cookie(COOKIE, fake)));
        assertTrue(isLoggedOut(new Cookie(COOKIE, "roskaa")));
        assertTrue(isLoggedOut(new Cookie(COOKIE, "")));
    }

    @Test
    void eachDeviceHasItsOwnToken() throws Exception {
        AppUser me = newUser();
        Cookie phone = loginCookie(me);
        Cookie laptop = loginCookie(me);

        assertFalse(phone.getValue().equals(laptop.getValue()), "Jokaisella laitteella on oma tunniste");
        assertEquals(2, tokenRows(me));
        assertTrue(isLoggedInAs(phone, me));
        assertTrue(isLoggedInAs(laptop, me));
    }

    @Test
    void aUsersCookie_neverLogsInAsSomeoneElse() throws Exception {
        AppUser me = newUser();
        AppUser other = newUser();
        Cookie cookie = loginCookie(me);

        Visit visit = visit(cookie);

        assertEquals(me.getUsername(), visit.username());
        assertFalse(other.getUsername().equals(visit.username()));
    }

    // ---- kumoaminen ----

    @Test
    void logout_clearsTheCookie_andRevokesTheToken() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);

        MockHttpServletResponse response = mvc().perform(post("/logout").with(csrf()).cookie(cookie))
                .andReturn().getResponse();

        assertEquals(302, response.getStatus());
        Cookie cleared = response.getCookie(COOKIE);
        assertNotNull(cleared, "Uloskirjautuminen tyhjentää evästeen selaimesta");
        assertEquals(0, cleared.getMaxAge());
        assertEquals(0, tokenRows(me), "Tunniste poistuu tietokannasta");
        assertTrue(isLoggedOut(cookie), "Vanha eväste ei enää päästä sisään");
    }

    @Test
    void logoutWithALiveSession_worksTheSameWay() throws Exception {
        AppUser me = newUser();
        MvcResult loginResult = mvc().perform(post("/login").param("username", me.getUsername())
                .param("password", PASSWORD).with(csrf())).andReturn();
        Cookie cookie = loginResult.getResponse().getCookie(COOKIE);
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        MockHttpServletResponse response = mvc().perform(post("/logout").with(csrf()).cookie(cookie).session(session))
                .andReturn().getResponse();

        assertEquals(302, response.getStatus());
        assertEquals(0, tokenRows(me));
        assertTrue(isLoggedOut(cookie));
    }

    @Test
    void logoutOnOneDevice_logsOutOnlyThatDevice() throws Exception {
        AppUser me = newUser();
        Cookie phone = loginCookie(me);
        Cookie laptop = loginCookie(me);

        mvc().perform(post("/logout").with(csrf()).cookie(phone));

        assertEquals(1, tokenRows(me), "Vain puhelimen tunniste kumoutuu");
        assertTrue(isLoggedOut(phone));
        assertTrue(isLoggedInAs(laptop, me), "Kannettava pysyy kirjautuneena");
    }

    @Test
    void deletingTheAccount_revokesTheCookie_andRemovesTheTokens() throws Exception {
        AppUser me = newUser();
        Cookie cookie = loginCookie(me);
        assertEquals(1, tokenRows(me));

        accounts.deleteAccount(me.getId(), PASSWORD);

        assertEquals(0, tokenRows(me), "Tilin poisto siivoaa tunnisteet");
        assertTrue(isLoggedOut(cookie), "Poistetun tilin eväste ei päästä sisään");
    }

    // ---- siivous ----

    @Test
    void cleanup_removesOnlyExpiredTokens() {
        String oldSeries = "vanha-" + UUID.randomUUID();
        String freshSeries = "tuore-" + UUID.randomUUID();
        insertToken(oldSeries, Instant.now().minus(days + 1L, ChronoUnit.DAYS));
        insertToken(freshSeries, Instant.now().minus(1, ChronoUnit.DAYS));

        int removed = cleanup.deleteExpired();

        assertTrue(removed >= 1);
        assertEquals(0, countSeries(oldSeries), "Vanhentunut tunniste siivotaan");
        assertEquals(1, countSeries(freshSeries), "Tuore tunniste säilyy");
        jdbc.update("delete from persistent_logins where series = ?", freshSeries);
    }

    private void insertToken(String series, Instant lastUsed) {
        jdbc.update("insert into persistent_logins (series, username, token, last_used) values (?, ?, ?, ?)",
                series, "ei-ketaan", "tunniste", Timestamp.from(lastUsed));
    }

    private long countSeries(String series) {
        return jdbc.queryForObject("select count(*) from persistent_logins where series = ?", Long.class, series);
    }
}
