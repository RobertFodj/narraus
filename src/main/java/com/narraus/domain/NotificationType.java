package com.narraus.domain;

/** Sovelluksen sisäisen ilmoituksen laji. */
public enum NotificationType {
    /** Joku alkoi seurata käyttäjää. */
    FOLLOWER,
    /** Joku tykkäsi käyttäjän julkaisusta (targetId = julkaisu, label = laji). */
    LIKE,
    /** Uusi kilpailu (targetId = kilpailu, label = nimi). */
    EVENT
}
