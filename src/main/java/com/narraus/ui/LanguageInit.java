package com.narraus.ui;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

/** Asettaa käyttöliittymän kielen (locale) selaimen kieli-evästeestä; ensimmäisellä kerralla suomi. */
@Component
public class LanguageInit implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(e -> e.getUI().setLocale(languageOf(VaadinService.getCurrentRequest()).locale()));
    }

    static Language languageOf(VaadinRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (Language.COOKIE.equals(c.getName())) return Language.fromCookie(c.getValue());
            }
        }
        return Language.FI;
    }
}
