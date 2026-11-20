package com.narraus.domain;

import java.util.Optional;

/** Paikannimen haku koordinaateiksi. */
public interface GeocodingService {

    record Place(String name, double latitude, double longitude) {}

    /** Etsii paikan nimellä; tyhjä, jos paikkaa ei löydy tai haku ei ole käytettävissä. */
    Optional<Place> search(String query);

    /**
     * Käänteishaku: ehdotettu nimi kartalta valitulle pisteelle (ks. {@link PlaceNames}), tai tyhjä, jos haku ei ole
     * käytettävissä.
     */
    default Optional<String> describe(double latitude, double longitude) {
        return Optional.empty();
    }

    /** Ehdotus pisteelle: nimi, ja jos sitä ei saada, koordinaatit. */
    default String suggestName(double latitude, double longitude) {
        return describe(latitude, longitude).orElseGet(() -> PlaceNames.coordinates(latitude, longitude));
    }
}
