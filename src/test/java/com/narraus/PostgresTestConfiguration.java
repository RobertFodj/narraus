package com.narraus;

import com.narraus.domain.GeocodingService;
import com.narraus.domain.PushDispatcher;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Oikea PostgreSQL testeille: Flyway ajaa samat migraatiot kuin tuotannossa, ja Hibernate validoi
 * entiteetit niitä vasten. Kontin versio pidetään samana kuin docker-compose.yml:ssä.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    /** Korvaa MapTilerin paikkahaun: testit eivät kutsu ulkoista palvelua. Tuntee vain "Päijänne" ja "Lahti". */
    @Bean
    @Primary
    GeocodingService testGeocoder() {
        return query -> {
            String q = query == null ? "" : query.toLowerCase();
            if (q.contains("päijänne")) return Optional.of(new GeocodingService.Place("Päijänne", 61.5, 25.5));
            if (q.contains("lahti")) return Optional.of(new GeocodingService.Place("Lahti", 60.98, 25.66));
            return Optional.empty();
        };
    }

    /** Push-viestit tallennetaan testeissä muistiin eikä lähetetä oikeaan push-palveluun. */
    @Bean
    @Primary
    RecordingPushSender recordingPushSender() {
        return new RecordingPushSender();
    }

    /** Lähetys tehdään samassa säikeessä, jotta testit ovat deterministisiä. */
    @Bean
    @Primary
    PushDispatcher synchronousPushDispatcher() {
        return Runnable::run;
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
