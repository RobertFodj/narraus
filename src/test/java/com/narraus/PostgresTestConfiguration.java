package com.narraus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Oikea PostgreSQL testeille: Flyway ajaa samat migraatiot kuin tuotannossa, ja Hibernate validoi
 * entiteetit niitä vasten. Kontin versio pidetään samana kuin docker-compose.yml:ssä.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
