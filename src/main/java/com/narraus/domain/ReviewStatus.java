package com.narraus.domain;

/** Ylläpitäjän merkintä kalasta kilpailussa. */
public enum ReviewStatus {
    /** Ylläpitäjä on kysynyt, onko saalis oikea. Kala lasketaan mukaan, mutta se on merkitty tarkistettavaksi. */
    QUESTIONED,
    /** Kala on poistettu kilpailusta, eikä sitä lasketa tuloksiin. */
    REJECTED
}
