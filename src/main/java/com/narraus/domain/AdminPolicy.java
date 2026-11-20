package com.narraus.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ylläpitäjä on käyttäjä, jonka tunnus on asetuksissa (narraus.admin-username, oletus "robbe"). Vain hän voi luoda,
 * muokata ja poistaa kilpailuja sekä tarkistaa kalat. Tunnus on varattu: kukaan muu ei voi vaihtaa sellaiseksi,
 * eikä ylläpitäjän tunnusta voi vaihtaa tai tiliä poistaa, jottei tunnus vapaudu muiden otettavaksi.
 */
@Component
public class AdminPolicy {

    private final String adminUsername;

    AdminPolicy(@Value("${narraus.admin-username:robbe}") String adminUsername) {
        this.adminUsername = adminUsername.trim().toLowerCase();
    }

    public boolean isAdmin(AppUser user) {
        return user != null && adminUsername.equals(user.getUsername());
    }

    public boolean isAdminUsername(String username) {
        return username != null && adminUsername.equals(username.trim().toLowerCase());
    }

    public String adminUsername() {
        return adminUsername;
    }
}
