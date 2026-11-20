package com.narraus;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.Viewport;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles.css")
/*
 * interactive-widget=resizes-content: puhelimen näppäimistö pienentää sivun omaa korkeutta (Android Chrome),
 * jolloin viestikenttä jää näppäimistön yläpuolelle ilman, että selain siirtelee sivua kirjoittaessa.
 */
@Viewport("width=device-width, initial-scale=1, interactive-widget=resizes-content")
@Push
@PWA(name = "Narraus", shortName = "Narraus", description = "Kalastajien saalis- ja viestisovellus",
     themeColor = "#1b5fc4", backgroundColor = "#f2f4f7")
public class Application implements AppShellConfigurator {

    /**
     * Pieneen kokoon suunniteltu yksinkertaistettu kuvake (SVG ja ICO). Muut kuvakkeet (manifest, iOS, splash)
     * generoi @PWA tiedostosta icons/icon.png.
     */
    @Override
    public void configurePage(AppShellSettings settings) {
        // Puhelimen näppäimistö pienentää sivun käytettävää aluetta (eikä peitä sisältöä sen päältä)
        settings.setViewport("width=device-width, initial-scale=1, interactive-widget=resizes-content");
        // Kielituki (käännökset selaimessa); ladataan heti sivun alussa, jotta sivu ei välähdä suomeksi
        settings.addInlineWithContents(Inline.Position.PREPEND, i18nScript(), Inline.Wrapping.JAVASCRIPT);
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
        settings.addFavIcon("icon", "favicon.ico", "16x16 32x32 48x48");
    }

    private static String i18nScript() {
        try (var in = Application.class.getResourceAsStream("/META-INF/resources/narraus-i18n.js")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | NullPointerException e) {
            throw new IllegalStateException("narraus-i18n.js puuttuu", e);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
