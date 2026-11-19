package com.narraus;

import com.vaadin.browserless.SecuredBrowserlessApplicationContext;
import com.vaadin.browserless.SpringBrowserlessApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

/** Jaettu selaimeton testikonteksti; käyttää sovelluksen omaa pääsynhallintaa (createSecured). */
@Configuration
public class BrowserlessContextConfiguration {

    @Bean
    SecuredBrowserlessApplicationContext<Authentication> browserlessApplicationContext(
            ApplicationContext applicationContext) {
        return SpringBrowserlessApplicationContext.createSecured(applicationContext, Application.class);
    }
}
