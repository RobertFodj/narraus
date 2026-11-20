package com.narraus.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Kielivalinta: maapallokuvake ja kielten valikko (Suomi, Svenska, Norsk, English). Valinta lataa uuden kielen
 * sanakirjan edistymispalkin kanssa ja muistaa vain valitun kielen (ks. narraus-i18n.js).
 */
public class LanguageMenu extends Button {

    private LanguageMenu(boolean withLabel) {
        super(VaadinIcon.GLOBE.create());
        addThemeVariants(ButtonVariant.TERTIARY);
        addClassName("language-menu");
        Language current = currentLanguage();
        if (withLabel) {
            addClassName("language-menu-labeled");
            setText("Kieli");
            Span name = new Span(current.nativeName());
            name.addClassName("language-menu-current");
            getElement().appendChild(name.getElement());
        } else {
            getElement().setAttribute("aria-label", "Kieli");
            getElement().setAttribute("title", "Kieli");
        }
        ContextMenu menu = new ContextMenu(this);
        menu.setOpenOnClick(true);
        for (Language l : Language.values()) {
            var item = menu.addItem(l.nativeName(), e -> select(l));
            item.getElement().setAttribute("lang", l.code());
            item.setCheckable(true);
            item.setChecked(l == current);
        }
    }

    /** Pelkkä kuvake (kirjautumis- ja rekisteröitymissivun oikea yläkulma). */
    public static LanguageMenu iconOnly() {
        return new LanguageMenu(false);
    }

    /** Kuvake ja teksti "Kieli" sekä nykyinen kieli (Oma tili -sivu). */
    public static LanguageMenu labeled() {
        return new LanguageMenu(true);
    }

    static Language currentLanguage() {
        UI ui = UI.getCurrent();
        if (ui != null && ui.getLocale() != null) {
            String lang = ui.getLocale().getLanguage();
            if (lang.equals("nb") || lang.equals("nn")) lang = "no";
            return Language.ofCode(lang).orElse(Language.FI);
        }
        return Language.FI;
    }

    private void select(Language language) {
        if (language == currentLanguage()) return;
        UI.getCurrent().getPage().executeJs("window.narrausLang && window.narrausLang.set($0)", language.code());
    }
}
