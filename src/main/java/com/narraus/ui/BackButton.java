package com.narraus.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouteConfiguration;

/**
 * Paluunuoli alisivuille: vie takaisin sinne, mistä tultiin (selaimen historia). Jos historiaa ei ole
 * (sivu avattu suoraan linkistä), palataan annettuun oletussivuun.
 */
public class BackButton extends Button {

    public BackButton(Class<? extends Component> fallback) {
        super(VaadinIcon.ARROW_LEFT.create());
        addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        addClassName("back-button");
        getElement().setAttribute("aria-label", "Takaisin");
        String fallbackUrl = "/" + RouteConfiguration.forApplicationScope().getUrl(fallback);
        addClickListener(e -> UI.getCurrent().getPage().executeJs(
                "if (window.history.length > 1) { window.history.back(); } else { window.location.assign($0); }",
                fallbackUrl));
    }
}
