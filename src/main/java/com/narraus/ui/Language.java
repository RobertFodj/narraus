package com.narraus.ui;

import java.util.Locale;
import java.util.Optional;

/** Sovelluksen kielet. Suomi on lähdekieli; muut kielet käännetään selaimessa sanakirjalla (narraus-i18n.js). */
public enum Language {
    FI("fi", "Suomi", Locale.of("fi")),
    SV("sv", "Svenska", Locale.of("sv")),
    NO("no", "Norsk", Locale.of("nb")),
    EN("en", "English", Locale.ENGLISH);

    /** Selaimen eväste, johon valittu kieli tallennetaan (ei muita kieliä). */
    public static final String COOKIE = "narraus-lang";

    private final String code;
    private final String nativeName;
    private final Locale locale;

    Language(String code, String nativeName, Locale locale) {
        this.code = code;
        this.nativeName = nativeName;
        this.locale = locale;
    }

    public String code() { return code; }
    public String nativeName() { return nativeName; }
    public Locale locale() { return locale; }

    public static Optional<Language> ofCode(String code) {
        for (Language l : values()) if (l.code.equals(code)) return Optional.of(l);
        return Optional.empty();
    }

    /** Nykyisen käyttöliittymän kieli (UI:n locale, jonka LanguageInit asettaa evästeestä); oletus suomi. */
    public static Language current() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui == null || ui.getLocale() == null) return FI;
        String lang = ui.getLocale().getLanguage();
        if (lang.equals("nb") || lang.equals("nn")) lang = "no";
        return ofCode(lang).orElse(FI);
    }

    /** Kieli evästeen arvosta; tuntematon tai puuttuva arvo on suomi. */
    public static Language fromCookie(String value) {
        return ofCode(value).orElse(FI);
    }
}
