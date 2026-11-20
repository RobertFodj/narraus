package com.narraus.domain;

/** Mitä kilpailussa mitataan. */
public enum EventMetric {
    WEIGHT("Suurin paino", "kg"),
    LENGTH("Pisin kala", "cm"),
    GIRTH("Suurin ympärysmitta", "cm"),
    COUNT("Eniten kaloja", "kpl");

    private final String label;
    private final String unit;

    EventMetric(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() { return label; }
    public String unit() { return unit; }

    /** Saaliin arvo tässä mittarissa, tai null jos mittaa ei ole annettu (silloin saalis ei kelpaa kilpailuun). */
    Double valueOf(CatchPost p) {
        return switch (this) {
            case WEIGHT -> p.getWeightKg();
            case LENGTH -> p.getLengthCm();
            case GIRTH -> p.getGirthCm();
            case COUNT -> 1.0;
        };
    }
}
