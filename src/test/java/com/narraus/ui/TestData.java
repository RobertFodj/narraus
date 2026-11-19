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
}
