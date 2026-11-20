package com.narraus;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vastaanottaa selaimen ilmoituksen, että sovellus ei käynnistynyt (esim. liian vanha selain). Ilmoitus kirjataan
 * lokiin selaimen tunnisteen kanssa, jotta syy näkyy ilman että käyttäjää tarvitsee kuulustella. Julkinen,
 * koska ilmoitus tulee ennen kirjautumista; koko ja määrä on rajattu.
 */
@RestController
class ClientErrorController {

    static final String PATH = "/client-error";
    private static final Logger LOG = Logger.getLogger(ClientErrorController.class.getName());
    private static final int MAX_LENGTH = 1500;
    private static final int MAX_PER_MINUTE = 30;

    private final AtomicLong windowStart = new AtomicLong();
    private final AtomicLong inWindow = new AtomicLong();

    @PostMapping(PATH)
    ResponseEntity<Void> report(@RequestBody(required = false) String body) {
        long now = System.currentTimeMillis();
        if (now - windowStart.get() > 60_000) {
            windowStart.set(now);
            inWindow.set(0);
        }
        if (inWindow.incrementAndGet() <= MAX_PER_MINUTE) {
            LOG.warning("Selain ei käynnistänyt sovellusta: " + clean(body));
        }
        return ResponseEntity.noContent().build();
    }

    /** Lyhennetty, yksirivinen teksti (ei rivinvaihtoja, joilla lokia voisi väärentää). */
    static String clean(String s) {
        if (s == null) return "";
        String one = s.replaceAll("[\\r\\n\\t]+", " ").strip();
        return one.length() > MAX_LENGTH ? one.substring(0, MAX_LENGTH) + "…" : one;
    }
}
