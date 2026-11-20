package com.narraus.domain;

/** Lähettää yhden push-viestin yhdelle tilaukselle. Testeissä korvataan, jotta oikeaa push-palvelua ei kutsuta. */
public interface PushSender {

    enum Result {
        /** Viesti hyväksyttiin. */
        OK,
        /** Tilaus on poistunut (selain peruutti sen): se pitää poistaa. */
        GONE,
        /** Muu virhe: yritetään myöhemmin uudelleen seuraavalla viestillä. */
        FAILED
    }

    /** icon on ilmoituksen kuvake (osoite) tai null, jolloin selain käyttää oletusta. */
    Result send(String endpoint, String p256dh, String auth, String title, String body, String icon);
}
