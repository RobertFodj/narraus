package com.narraus.domain;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Paikkahaku MapTilerin Geocoding API:lla. Ilman avainta ei hae mitään. Suosii Suomea. */
@Component
class MapTilerGeocoder implements GeocodingService {

    private static final Logger LOG = Logger.getLogger(MapTilerGeocoder.class.getName());

    private final String key;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    MapTilerGeocoder(@Value("${narraus.maptiler-key:}") String key) {
        this.key = key;
    }

    @Override
    public Optional<Place> search(String query) {
        if (key == null || key.isBlank() || query == null || query.isBlank()) return Optional.empty();
        try {
            String q = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8).replace("+", "%20");
            URI uri = URI.create("https://api.maptiler.com/geocoding/" + q + ".json?limit=1&country=fi&language=fi&key="
                    + URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                LOG.log(Level.WARNING, "Paikkahaku epäonnistui, HTTP {0}", res.statusCode());
                return Optional.empty();
            }
            JsonNode features = mapper.readTree(res.body()).path("features");
            if (!features.isArray() || features.isEmpty()) return Optional.empty();
            JsonNode f = features.get(0);
            JsonNode center = f.path("center");
            if (!center.isArray() || center.size() < 2) return Optional.empty();
            String name = f.path("place_name").asString(f.path("text").asString(query));
            return Optional.of(new Place(name, center.get(1).asDouble(), center.get(0).asDouble()));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Paikkahaku epäonnistui", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
