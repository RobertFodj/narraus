package com.narraus;

import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;

/** Pysyvien kirjautumistunnisteiden tietokantavarasto, jolla yksittäisen laitteen (series) voi kumota. */
class SeriesTokenRepository extends JdbcTokenRepositoryImpl {

    /** Poistaa yhden laitteen tunnisteen. Käyttäjän muut laitteet pysyvät kirjautuneina. */
    void removeSeries(String series) {
        getJdbcTemplate().update("delete from persistent_logins where series = ?", series);
    }
}
