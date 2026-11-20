package com.narraus.ui;

import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventStatus;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Kilpailujen yhteiset muotoilut. */
final class EventFormat {

    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm").withZone(ZoneId.systemDefault());
    static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d.M. HH:mm").withZone(ZoneId.systemDefault());

    private EventFormat() {}

    static String interval(CompetitionEvent e) {
        return TIME.format(e.getStartsAt()) + " – " + TIME.format(e.getEndsAt());
    }

    /** "Suurin paino" tai "Suurin paino · Hauki". */
    static String goal(CompetitionEvent e) {
        return e.getSpecies() == null ? e.getMetric().label() : e.getMetric().label() + " · " + Species.display(e.getSpecies());
    }

    static String statusClass(EventStatus s) {
        return "event-badge event-" + s.name().toLowerCase();
    }
}
