package com.narraus.ui;

import com.narraus.domain.CompetitionEvent;
import com.narraus.domain.EventMetric;
import com.narraus.domain.EventService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Ylläpitäjän lomake kilpailun luomiseen ja muokkaamiseen. */
class EventDialog extends Dialog {

    EventDialog(EventService events, CurrentUser currentUser, CompetitionEvent existing,
                Consumer<CompetitionEvent> onSaved) {
        setHeaderTitle(existing == null ? "Uusi kilpailu" : "Muokkaa kilpailua");
        setWidth("min(36em, 95vw)");
        ZoneId zone = ZoneId.systemDefault();
        Locale fi = Locale.of("fi", "FI");

        TextField name = new TextField("Nimi");
        name.setRequired(true);
        name.setMaxLength(120);
        name.setPlaceholder("esim. Syysahven-kisa");
        TextArea description = new TextArea("Kuvaus ja säännöt");
        description.setMaxLength(2000);

        DateTimePicker starts = new DateTimePicker("Alkaa");
        DateTimePicker ends = new DateTimePicker("Päättyy");
        for (DateTimePicker p : List.of(starts, ends)) {
            p.setLocale(fi);
            p.setStep(Duration.ofMinutes(1));
        }
        LocalDateTime now = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES);

        Select<EventMetric> metric = new Select<>();
        metric.setLabel("Mitä mitataan");
        metric.setItems(EventMetric.values());
        metric.setItemLabelGenerator(EventMetric::label);
        metric.setValue(EventMetric.WEIGHT);
        metric.setHelperText("Suurin paino, pisin kala, suurin ympärysmitta tai eniten kaloja");

        TextField species = new TextField("Laji (tyhjä = kaikki lajit)");
        species.setMaxLength(80);
        species.setPlaceholder("esim. Hauki");

        IntegerField counted = new IntegerField("Kuinka monta parasta kalaa lasketaan");
        counted.setMin(1);
        counted.setMax(10);
        counted.setStepButtonsVisible(true);
        counted.setValue(1);
        counted.setHelperText("1 = vain paras kala. 3 = kolmen parhaan summa.");
        metric.addValueChangeListener(e -> counted.setVisible(e.getValue() != EventMetric.COUNT));

        if (existing == null) {
            starts.setValue(now);
            ends.setValue(now.plusDays(7));
        } else {
            starts.setValue(LocalDateTime.ofInstant(existing.getStartsAt(), zone).truncatedTo(ChronoUnit.MINUTES));
            ends.setValue(LocalDateTime.ofInstant(existing.getEndsAt(), zone).truncatedTo(ChronoUnit.MINUTES));
            name.setValue(existing.getName());
            description.setValue(existing.getDescription() == null ? "" : existing.getDescription());
            metric.setValue(existing.getMetric());
            species.setValue(existing.getSpecies() == null ? "" : existing.getSpecies());
            counted.setValue(existing.getCountedFish());
        }

        Paragraph rule = new Paragraph("Kilpailuun lasketaan vain saaliit, joissa on kuva ja jotka on pyydetty "
                + "kilpailun aikana kilpailuun liittyneiltä. Kuvattomat saaliit eivät kelpaa.");
        rule.getStyle().set("opacity", "0.8").set("font-size", "0.9em");

        // Käännökset (valinnaisia): nimi ja kuvaus ruotsiksi, norjaksi ja englanniksi; puuttuessa näytetään suomi
        java.util.Map<Language, TextField> nameTranslations = new java.util.LinkedHashMap<>();
        java.util.Map<Language, TextArea> descriptionTranslations = new java.util.LinkedHashMap<>();
        VerticalLayout translations = new VerticalLayout();
        translations.setPadding(false);
        translations.add(new Paragraph("Jos käännöstä ei ole, näytetään suomenkielinen teksti."));
        for (Language l : Language.values()) {
            if (l == Language.FI) continue;
            TextField tn = new TextField("Nimi (" + l.nativeName() + ")");
            tn.setMaxLength(120);
            tn.setWidthFull();
            TextArea td = new TextArea("Kuvaus (" + l.nativeName() + ")");
            td.setMaxLength(2000);
            td.setWidthFull();
            if (existing != null) {
                if (existing.translatedName(l.code()) != null) tn.setValue(existing.translatedName(l.code()));
                if (existing.translatedDescription(l.code()) != null) td.setValue(existing.translatedDescription(l.code()));
            }
            nameTranslations.put(l, tn);
            descriptionTranslations.put(l, td);
            translations.add(tn, td);
        }
        Details translationsBox = new Details("Käännökset (valinnainen)", translations);
        translationsBox.addClassName("event-translations");

        FormLayout form = new FormLayout(name, description, translationsBox, starts, ends, metric, species, counted, rule);
        form.setColspan(translationsBox, 2);
        form.setColspan(name, 2);
        form.setColspan(description, 2);
        form.setColspan(rule, 2);
        add(form);

        Button save = new Button(existing == null ? "Luo kilpailu" : "Tallenna", e -> {
            if (name.isEmpty()) {
                name.setInvalid(true);
                name.setErrorMessage("Nimi vaaditaan");
                return;
            }
            if (starts.isEmpty() || ends.isEmpty()) {
                Notification.show("Anna alkamis- ja päättymisaika").addThemeVariants(NotificationVariant.ERROR);
                return;
            }
            try {
                int n = counted.getValue() == null ? 1 : counted.getValue();
                CompetitionEvent saved = existing == null
                        ? events.create(currentUser.get(), name.getValue(), description.getValue(),
                                starts.getValue().atZone(zone).toInstant(), ends.getValue().atZone(zone).toInstant(),
                                metric.getValue(), canonicalSpecies(species.getValue()), n)
                        : events.update(existing.getId(), currentUser.get(), name.getValue(), description.getValue(),
                                starts.getValue().atZone(zone).toInstant(), ends.getValue().atZone(zone).toInstant(),
                                metric.getValue(), canonicalSpecies(species.getValue()), n);
                java.util.Map<String, EventService.Translation> byLanguage = new java.util.LinkedHashMap<>();
                nameTranslations.forEach((l, tn) -> byLanguage.put(l.code(),
                        new EventService.Translation(tn.getValue(), descriptionTranslations.get(l).getValue())));
                saved = events.translate(saved.getId(), currentUser.get(), byLanguage);
                close();
                onSaved.accept(saved);
            } catch (SecurityException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
                close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        getFooter().add(new Button("Peruuta", e -> close()), save);
    }

    /** Laji tallennetaan suomenkielisenä, vaikka se kirjoitettaisiin toisella kielellä (esim. "pike" -> "Hauki"). */
    private static String canonicalSpecies(String typed) {
        return Species.canonical(typed).orElse(typed);
    }
}
