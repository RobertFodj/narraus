package com.narraus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Kaikille näkymätesteille yhteinen konfiguraatio: koko sovellus + Testcontainers-Postgres. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = "narraus.upload-dir=target/test-uploads")
@Import(PostgresTestConfiguration.class)
public @interface NarrausTest {
}
