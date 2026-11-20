package com.narraus.domain;

import java.util.Optional;

/** Paikannimen haku koordinaateiksi. */
public interface GeocodingService {

    record Place(String name, double latitude, double longitude) {}

    /** Etsii paikan nimellä; tyhjä, jos paikkaa ei löydy tai haku ei ole käytettävissä. */
    Optional<Place> search(String query);
}
