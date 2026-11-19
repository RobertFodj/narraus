package com.narraus.ui;

import com.narraus.domain.UserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("register")
@PageTitle("Rekisteröidy | Narraus")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    public RegisterView(UserService users) {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        TextField username = new TextField("Käyttäjätunnus");
        TextField displayName = new TextField("Näytettävä nimi");
        PasswordField password = new PasswordField("Salasana (väh. 8 merkkiä)");
        PasswordField confirm = new PasswordField("Salasana uudelleen");
        for (var f : new com.vaadin.flow.component.textfield.TextFieldBase<?, ?>[]{username, displayName, password, confirm}) {
            f.setWidth("20em");
        }

        Button register = new Button("Luo tunnus", e -> {
            if (!password.getValue().equals(confirm.getValue())) {
                Notification.show("Salasanat eivät täsmää").addThemeVariants(NotificationVariant.ERROR);
                return;
            }
            try {
                users.register(username.getValue(), displayName.getValue(), password.getValue());
                Notification.show("Tunnus luotu, voit nyt kirjautua").addThemeVariants(NotificationVariant.SUCCESS);
                UI.getCurrent().navigate(LoginView.class);
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        register.addThemeVariants(ButtonVariant.PRIMARY);

        add(new H1("🎣 Liity Narraukseen"), username, displayName, password, confirm, register,
                new RouterLink("Minulla on jo tunnus", LoginView.class));
    }
}
