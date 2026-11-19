package com.narraus.ui;

import com.narraus.domain.AppUser;
import com.narraus.domain.UserService;
import com.vaadin.flow.spring.security.AuthenticationContext;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final AuthenticationContext auth;
    private final UserService users;

    CurrentUser(AuthenticationContext auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    public AppUser get() {
        return auth.getPrincipalName().flatMap(users::find)
                .orElseThrow(() -> new IllegalStateException("Ei kirjautunutta käyttäjää"));
    }

    public void logout() {
        auth.logout();
    }
}
