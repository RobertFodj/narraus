package com.narraus.domain;

/** Sovelluksen sisäisen ilmoituksen laji. */
public enum NotificationType {
    /** Joku alkoi seurata käyttäjää. */
    FOLLOWER,
    /** Joku tykkäsi käyttäjän julkaisusta (targetId = julkaisu, label = laji). */
    LIKE,
    /** Uusi kilpailu (targetId = kilpailu, label = nimi). */
    EVENT,
    /** Ylläpitäjä poisti käyttäjän julkaisun (targetId = päätös, label = laji). */
    MODERATION_DELETED,
    /** Ylläpitäjä muokkasi käyttäjän julkaisua (targetId = päätös, label = laji). */
    MODERATION_EDITED,
    /** Käyttäjä lähetti valituksen tai palautteen ylläpitäjälle (targetId = päätös). */
    APPEAL
}
