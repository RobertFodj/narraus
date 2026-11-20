package com.narraus.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Yleisimmät kalalajit. Julkaisuun tallennetaan lajin suomenkielinen nimi (kielestä riippumaton tunniste), ja
 * katsoja näkee sen omalla kielellään. Valikko ja haku käyttävät samoja nimiä. Muut (itse kirjoitetut) lajit
 * näytetään sellaisenaan.
 */
public final class Species {

    private record Names(String sv, String no, String en) {}

    /** Valikon järjestys: yleisimmät, särkikalat, lohikalat. */
    private static final Map<String, Names> TABLE = new LinkedHashMap<>();

    static {
        TABLE.put("Ahven", new Names("Aborre", "Åbor", "Perch"));
        TABLE.put("Hauki", new Names("Gädda", "Gjedde", "Pike"));
        TABLE.put("Kuha", new Names("Gös", "Gjørs", "Zander"));
        TABLE.put("Made", new Names("Lake", "Lake", "Burbot"));
        TABLE.put("Särki", new Names("Mört", "Mort", "Roach"));
        TABLE.put("Lahna", new Names("Braxen", "Brasme", "Bream"));
        TABLE.put("Salakka", new Names("Löja", "Laue", "Bleak"));
        TABLE.put("Pasuri", new Names("Björkna", "Flire", "Silver bream"));
        TABLE.put("Sorva", new Names("Sarv", "Sørv", "Rudd"));
        TABLE.put("Säyne", new Names("Id", "Ide", "Ide"));
        TABLE.put("Kirjolohi", new Names("Regnbåge", "Regnbueørret", "Rainbow trout"));
        TABLE.put("Siika", new Names("Sik", "Sik", "Whitefish"));
        TABLE.put("Muikku", new Names("Siklöja", "Lagesild", "Vendace"));
        TABLE.put("Taimen", new Names("Öring", "Ørret", "Brown trout"));
        TABLE.put("Lohi", new Names("Lax", "Laks", "Salmon"));
        TABLE.put("Harjus", new Names("Harr", "Harr", "Grayling"));
    }

    private Species() {}

    /** Lajit tallennettavassa (suomenkielisessä) muodossa valikon järjestyksessä. */
    public static List<String> all() {
        return List.copyOf(TABLE.keySet());
    }

    /** Lajin nimi annetulla kielellä; tuntematon (itse kirjoitettu) laji palautetaan sellaisenaan. */
    public static String display(String stored, Language language) {
        if (stored == null) return null;
        Names n = TABLE.get(stored);
        if (n == null) return stored;
        return switch (language) {
            case FI -> stored;
            case SV -> n.sv();
            case NO -> n.no();
            case EN -> n.en();
        };
    }

    /** Lajin nimi nykyisellä käyttöliittymän kielellä. */
    public static String display(String stored) {
        return display(stored, Language.current());
    }

    /**
     * Tunnistaa kirjoitetun nimen millä tahansa kielellä (isot/pienet kirjaimet ja ylimääräiset välit ohitetaan)
     * ja palauttaa lajin tallennettavan muodon, tai tyhjän, jos kyse ei ole tunnetusta lajista.
     */
    public static Optional<String> canonical(String typed) {
        if (typed == null || typed.isBlank()) return Optional.empty();
        String t = typed.strip().toLowerCase(Locale.ROOT);
        for (var e : TABLE.entrySet()) {
            Names n = e.getValue();
            if (e.getKey().toLowerCase(Locale.ROOT).equals(t) || n.sv().toLowerCase(Locale.ROOT).equals(t)
                    || n.no().toLowerCase(Locale.ROOT).equals(t) || n.en().toLowerCase(Locale.ROOT).equals(t)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }
}
