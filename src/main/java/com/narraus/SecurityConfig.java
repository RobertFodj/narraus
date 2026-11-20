package com.narraus;

import com.narraus.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** Avain tunnistaa tämän käynnistyksen kirjautumisobjektit. Evästeeseen se ei mene, joten sen ei tarvitse säilyä. */
    private final String rememberMeKey = UUID.randomUUID().toString();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, PersistentLoginService rememberMe) throws Exception {
        // Sovelluksen kuvakkeet ja kielitiedostot ovat julkisia (kirjautumissivu tarvitsee ne ennen kirjautumista)
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/icons/**", "/i18n/**").permitAll());
        // Pysy kirjautuneena: aina päällä, kantaan tallennettu ja kumottavissa (ks. PersistentLoginService)
        http.rememberMe(rm -> rm.rememberMeServices(rememberMe).key(rememberMeKey));
        http.with(VaadinSecurityConfigurer.vaadin(), c -> c.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    PersistentLoginService persistentLoginService(UserDetailsService userDetailsService,
                                                  SeriesTokenRepository tokens,
                                                  @Value("${narraus.remember-me-days:90}") int rememberMeDays) {
        return new PersistentLoginService(rememberMeKey, userDetailsService, tokens, rememberMeDays);
    }

    @Bean
    SeriesTokenRepository persistentTokenRepository(DataSource dataSource) {
        SeriesTokenRepository repository = new SeriesTokenRepository();
        repository.setDataSource(dataSource); // taulu luodaan migraatiolla V8, ei Springin omalla alustuksella
        return repository;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
