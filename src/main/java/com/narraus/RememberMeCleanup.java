package com.narraus;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Siivoaa vanhentuneet "pysy kirjautuneena" -tunnisteet. Vanhentunutta tunnistetta ei hyväksytä enää
 * käytössä, mutta rivi jäisi muuten tauluun ikuisesti.
 */
@Component
@EnableScheduling
class RememberMeCleanup {

    private static final Logger LOG = Logger.getLogger(RememberMeCleanup.class.getName());

    private final JdbcTemplate jdbc;
    private final int rememberMeDays;

    RememberMeCleanup(JdbcTemplate jdbc, @Value("${narraus.remember-me-days:90}") int rememberMeDays) {
        this.jdbc = jdbc;
        this.rememberMeDays = rememberMeDays;
    }

    /** Joka yö klo 03:17. Palauttaa poistettujen rivien määrän. */
    @Scheduled(cron = "0 17 3 * * *")
    int deleteExpired() {
        Timestamp limit = Timestamp.from(Instant.now().minus(rememberMeDays, ChronoUnit.DAYS));
        int removed = jdbc.update("delete from persistent_logins where last_used < ?", limit);
        if (removed > 0) LOG.info("Poistettiin " + removed + " vanhentunutta kirjautumistunnistetta");
        return removed;
    }
}
