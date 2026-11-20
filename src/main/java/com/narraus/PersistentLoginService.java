package com.narraus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * "Pysy kirjautuneena" tietokantaan tallennetulla tunnisteella laitetta kohti.
 *
 * <p>Poikkeaa Springin oletuksesta kolmessa kohdassa:
 * <ul>
 *   <li>Tunnistetta ei kierrätetä jokaisella pyynnöllä. Oletustoteutus pitää rinnakkaisia pyyntöjä samalla
 *       evästeellä varkautena (Vaadin tekee sivua avatessa niitä useita), kumoaa kaikki tunnisteet ja antaa
 *       virhesivun. Tunniste on satunnainen ja kantaan tallennettu, joten se voidaan silti kumota.</li>
 *   <li>Käyttö pidentää voimassaoloa (liukuva ikkuna): eväste ja viimeisin käyttöaika uusitaan korkeintaan
 *       kerran vuorokaudessa.</li>
 *   <li>Uloskirjautuminen kumoaa vain sen laitteen tunnisteen, eikä käyttäjän muita laitteita.</li>
 * </ul>
 * Väärä tunniste oikealla sarjalla hylätään mutta ei kumoa käyttäjän muita tunnisteita, jottei sarjan
 * tuntevaa hyökkääjää voisi käyttää kirjaamaan käyttäjää ulos.
 */
class PersistentLoginService extends PersistentTokenBasedRememberMeServices {

    private static final long REFRESH_AFTER_MILLIS = 24L * 60 * 60 * 1000;

    private final SeriesTokenRepository tokens;

    PersistentLoginService(String key, UserDetailsService userDetailsService, SeriesTokenRepository tokens,
                           int validityDays) {
        super(key, userDetailsService, tokens);
        this.tokens = tokens;
        setTokenValiditySeconds(validityDays * 24 * 60 * 60);
        setAlwaysRemember(true); // Vaadinin LoginForm ei tarjoa valintaruutua
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
                                                 HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new InvalidCookieException("Cookie token did not contain 2 tokens, but contained "
                    + java.util.Arrays.asList(cookieTokens));
        }
        String series = cookieTokens[0];
        String token = cookieTokens[1];
        PersistentRememberMeToken stored = tokens.getTokenForSeries(series);
        if (stored == null) {
            throw new RememberMeAuthenticationException("No persistent token found for series id: " + series);
        }
        if (!token.equals(stored.getTokenValue())) {
            throw new InvalidCookieException("Remember-me token does not match the series");
        }
        long age = System.currentTimeMillis() - stored.getDate().getTime();
        if (age > getTokenValiditySeconds() * 1000L) {
            tokens.removeSeries(series);
            throw new RememberMeAuthenticationException("Remember-me login has expired");
        }
        if (age > REFRESH_AFTER_MILLIS) {
            tokens.updateToken(series, stored.getTokenValue(), new Date());
            setCookie(new String[] {series, token}, getTokenValiditySeconds(), request, response);
        }
        return getUserDetailsService().loadUserByUsername(stored.getUsername());
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String cookie = extractRememberMeCookie(request);
        if (cookie != null && !cookie.isEmpty()) {
            try {
                String[] parts = decodeCookie(cookie);
                if (parts.length == 2) tokens.removeSeries(parts[0]);
            } catch (InvalidCookieException ignored) {
                // roskaeväste: ei mitään kumottavaa
            }
        }
        cancelCookie(request, response);
    }
}
