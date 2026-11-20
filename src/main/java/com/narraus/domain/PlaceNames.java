package com.narraus.domain;

import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Päättää, minkä nimen kartalta valitulle pisteelle ehdotetaan (MapTilerin käänteishaun tuloksista):
 * lähin paikka, jos se on enintään 500 m päässä; muuten kaupunki (kunta); muuten maakunta; muuten maa;
 * muuten koordinaatit.
 */
public final class PlaceNames {

    /** Lähimmän paikan enimmäisetäisyys valitusta pisteestä metreinä. */
    public static final double NEAR_METERS = 500;

    /** Nimetyt paikat, joista lähin voidaan ehdottaa (ei katuja, osoitteita eikä postinumeroita). */
    private static final Set<String> PLACE_TYPES = Set.of("poi", "place", "locality", "neighbourhood",
            "municipal_district", "major_landform");
    private static final Set<String> CITY_TYPES = Set.of("municipality", "joint_municipality");

    private PlaceNames() {}

    /** Ehdotettu nimi tai tyhjä, jos tuloksista ei löydy mitään käyttökelpoista (silloin käytetään koordinaatteja). */
    public static String choose(JsonNode features, double lat, double lon) {
        if (features == null || !features.isArray()) return null;
        String city = firstText(features, CITY_TYPES);

        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (JsonNode f : features) {
            if (!hasType(f, PLACE_TYPES)) continue;
            JsonNode center = f.path("center");
            if (!center.isArray() || center.size() < 2) continue;
            double d = distanceMeters(lat, lon, center.get(1).asDouble(), center.get(0).asDouble());
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = f.path("text").asString("");
            }
        }
        if (nearest != null && !nearest.isBlank() && nearestDistance <= NEAR_METERS) {
            // Kunta mukaan tarkentamaan, ellei nimi ole sama
            return city != null && !city.equalsIgnoreCase(nearest) ? nearest + ", " + city : nearest;
        }
        if (city != null) return city;
        String region = firstText(features, Set.of("region"));
        if (region != null) return region;
        return firstText(features, Set.of("country"));
    }

    /** Koordinaatit tekstinä, esim. "61.5000, 25.5000" (viimeinen vaihtoehto). */
    public static String coordinates(double lat, double lon) {
        return String.format(Locale.ROOT, "%.4f, %.4f", lat, lon);
    }

    private static boolean hasType(JsonNode feature, Set<String> types) {
        JsonNode t = feature.path("place_type");
        if (!t.isArray()) return false;
        for (JsonNode n : t) if (types.contains(n.asString(""))) return true;
        return false;
    }

    private static String firstText(JsonNode features, Set<String> types) {
        for (JsonNode f : features) {
            if (hasType(f, types)) {
                String text = f.path("text").asString("");
                if (!text.isBlank()) return text;
            }
        }
        return null;
    }

    /** Kahden pisteen etäisyys metreinä (haversine). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
