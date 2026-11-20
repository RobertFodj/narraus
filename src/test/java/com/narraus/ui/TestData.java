package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import java.util.UUID;

/** Testidatan apurit. Kaikki testit jakavat saman kannan, joten nimet tehdään yksilöllisiksi. */
final class TestData {

    private TestData() {
    }

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Luo käyttäjän, jonka näyttönimi on yksilöllinen ("Kalle-1a2b3c4d"). */
    static AppUser newUser(UserService users, String displayNamePrefix) {
        return users.register(unique("u"), unique(displayNamePrefix), "salasana123");
    }

    /** Oma tili -sivun osio avataan napista; sisältö rakennetaan vasta avattaessa. */
    static void openSection(com.vaadin.browserless.BrowserlessUIContext window, String button) {
        var b = window.find(com.vaadin.flow.component.button.Button.class).all().stream()
                .filter(x -> button.equals(x.getText())).findFirst().orElseThrow();
        window.test(b).click();
    }

    @SuppressWarnings("unchecked")
    static com.vaadin.flow.component.combobox.ComboBox<String> species(com.vaadin.browserless.BrowserlessUIContext window) {
        return (com.vaadin.flow.component.combobox.ComboBox<String>) window.find(com.vaadin.flow.component.combobox.ComboBox.class)
                .all().stream().filter(c -> "Kalalaji".equals(c.getLabel())).findFirst().orElseThrow();
    }
}
