package com.narraus.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@PageTitle("Kirjaudu | Narraus")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Kirjaudu sisään");
        i18n.getForm().setUsername("Käyttäjätunnus");
        i18n.getForm().setPassword("Salasana");
        i18n.getForm().setSubmit("Kirjaudu");
        i18n.getErrorMessage().setTitle("Kirjautuminen epäonnistui");
        i18n.getErrorMessage().setMessage("Tarkista tunnus ja salasana.");
        login.setI18n(i18n);
        login.setAction("login");
        login.setForgotPasswordButtonVisible(false);

        H1 title = new H1("🎣 Narraus");
        add(title, login, new RouterLink("Ei vielä tunnusta? Rekisteröidy", RegisterView.class));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }
}
