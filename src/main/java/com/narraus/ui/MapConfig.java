package com.narraus.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.vaadin.addons.maplibre.MapLibre;

/** Karttojen yhteinen asetus: MapTiler-tyyli ja alkunäkymä Suomen päällä. */
@Component
class MapConfig {

    /** Suomen keskipiste ja zoom, jolla koko maa mahtuu näkyviin. */
    static final double FINLAND_LONGITUDE = 25.7;
    static final double FINLAND_LATITUDE = 64.9;
    static final double FINLAND_ZOOM = 4.4;

    private final String styleUrl;

    MapConfig(@Value("${narraus.maptiler-key:}") String key, @Value("${narraus.map-style:outdoor-v4}") String style) {
        // Ilman avainta käytetään MapLibren avointa demokarttaa, jotta näkymä ei hajoa.
        this.styleUrl = key == null || key.isBlank()
                ? "https://demotiles.maplibre.org/style.json"
                : "https://api.maptiler.com/maps/" + style + "/style.json?key="
                        + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    String styleUrl() {
        return styleUrl;
    }

    /** Uusi kartta, joka alkaa Suomen päältä. */
    MapLibre newMap() {
        MapLibre map = new MapLibre(styleUrl);
        map.setCenter(FINLAND_LONGITUDE, FINLAND_LATITUDE);
        map.setZoomLevel(FINLAND_ZOOM);
        return map;
    }
}
