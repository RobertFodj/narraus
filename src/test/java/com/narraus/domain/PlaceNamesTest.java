package com.narraus.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Kartalta valitun pisteen nimiehdotus: lähin paikka (≤ 500 m) → kaupunki → maakunta → maa → koordinaatit. */
class PlaceNamesTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static JsonNode features(String json) {
        return MAPPER.readTree(json).path("features");
    }

    /** Piste 61.0, 25.45 (Hollola). Työtjärvi-paikan keskipiste on noin 0,9 km päässä. */
    private static final String HOLLOLA = """
            {"features":[
             {"place_type":["road"],"text":"yhdystie 3161","center":[25.4088,61.0393]},
             {"place_type":["postal_code"],"text":"15880","center":[25.445,60.9899]},
             {"place_type":["locality"],"text":"Työtjärvi","center":[25.4621,60.9922]},
             {"place_type":["municipality"],"text":"Hollola","center":[25.5164,60.9876]},
             {"place_type":["region"],"text":"Päijät-Häme","center":[25.6613,60.9826]},
             {"place_type":["country"],"text":"Suomi","center":[25.9209,63.2467]}]}""";

    @Test
    void nearbyPlace_within500m_isSuggested_withItsMunicipality() {
        // Piste lähellä Työtjärveä (noin 100 m)
        String name = PlaceNames.choose(features(HOLLOLA), 60.9925, 25.4630);

        assertEquals("Työtjärvi, Hollola", name);
    }

    @Test
    void placeFartherThan500m_fallsBackToTheCity() {
        String name = PlaceNames.choose(features(HOLLOLA), 61.0, 25.45);

        assertEquals("Hollola", name, "Työtjärvi on yli 500 m päässä: kaupunki (kunta)");
    }

    @Test
    void withoutACity_theRegionIsUsed_andWithoutThat_theCountry() {
        String onlyRegion = """
                {"features":[{"place_type":["region"],"text":"Lappi","center":[25.0,67.0]},
                 {"place_type":["country"],"text":"Suomi","center":[25.9,63.2]}]}""";
        String onlyCountry = """
                {"features":[{"place_type":["country"],"text":"Suomi","center":[25.9,63.2]},
                 {"place_type":["continental_marine"],"text":"Eurooppa","center":[28.2,55.8]}]}""";

        assertEquals("Lappi", PlaceNames.choose(features(onlyRegion), 67.1, 25.1));
        assertEquals("Suomi", PlaceNames.choose(features(onlyCountry), 63.0, 26.0));
    }

    @Test
    void nothingUseful_givesNull_andTheFallbackIsTheCoordinates() {
        String nothing = """
                {"features":[{"place_type":["continental_marine"],"text":"Eurooppa","center":[28.2,55.8]}]}""";

        assertNull(PlaceNames.choose(features(nothing), 63.0, 26.0));
        assertNull(PlaceNames.choose(MAPPER.readTree("{\"features\":[]}").path("features"), 63.0, 26.0));
        assertEquals("61.5000, 25.5000", PlaceNames.coordinates(61.5, 25.5));

        GeocodingService unavailable = q -> Optional.empty();
        assertEquals("61.5000, 25.5000", unavailable.suggestName(61.5, 25.5),
                "Ilman paikkapalvelua ehdotetaan koordinaatteja");
    }

    @Test
    void streetsAndAddresses_areNotSuggestedAsPlaces() {
        String onlyStreet = """
                {"features":[{"place_type":["address"],"text":"Ritokuja","center":[25.6848,61.2039]},
                 {"place_type":["municipality"],"text":"Asikkala","center":[25.53,61.175]}]}""";

        assertEquals("Asikkala", PlaceNames.choose(features(onlyStreet), 61.2039, 25.6848));
    }

    @Test
    void largeLandform_whoseCentreIsFarAway_doesNotWinOverTheCity() {
        // Päijänne: keskipiste kaukana, joten kaupunki valitaan
        String lake = """
                {"features":[{"place_type":["municipality"],"text":"Asikkala","center":[25.53,61.175]},
                 {"place_type":["major_landform"],"text":"Päijänne","center":[25.5273,61.6464]}]}""";

        assertEquals("Asikkala", PlaceNames.choose(features(lake), 61.2, 25.7));
    }

    @Test
    void distance_isInMeters() {
        double d = PlaceNames.distanceMeters(61.0, 25.0, 61.0, 25.0 + 0.01);

        assertTrue(d > 500 && d < 600, "0,01° pituusastetta 61° leveydellä on noin 540 m, oli " + d);
    }
}
