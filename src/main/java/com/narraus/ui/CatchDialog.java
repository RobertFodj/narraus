package com.narraus.ui;

import com.narraus.domain.CatchPost;
import com.narraus.domain.GeocodingService;
import com.narraus.domain.PostService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.vaadin.addons.maplibre.MapLibre;
import org.vaadin.addons.maplibre.Marker;

/** Lomake uuden saaliin lisäämiseen. Sijainnin voi valita kartalta tai kirjoittamalla paikan nimen. */
class CatchDialog extends Dialog {

    private static final double PLACE_ZOOM = 11;

    private final GeocodingService geocoder;
    private final MapLibre map;
    private final Span pinInfo = new Span("Ei pistettä kartalla. Klikkaa karttaa tai hae paikka nimellä.");
    private final Button clearPin = new Button("Poista piste", VaadinIcon.CLOSE_SMALL.create(), e -> clearPin());
    private Marker pin;
    private Double latitude;
    private Double longitude;
    /** Kirjoitettu paikka paikannetaan tallennettaessa, ellei käyttäjä ole itse poistanut pistettä. */
    private boolean autoLocate = true;

    private byte[] photo;
    private String photoType;

    /** Uusi julkaisu. */
    CatchDialog(PostService posts, CurrentUser currentUser, MapConfig mapConfig, GeocodingService geocoder,
                Runnable onSaved) {
        this(posts, currentUser, mapConfig, geocoder, null, onSaved);
    }

    /** Muokkaa olemassa olevaa omaa julkaisua (existing != null) tai luo uuden (null). */
    CatchDialog(PostService posts, CurrentUser currentUser, MapConfig mapConfig, GeocodingService geocoder,
                CatchPost existing, Runnable onSaved) {
        this.geocoder = geocoder;
        setHeaderTitle(existing == null ? "Uusi saalis" : "Muokkaa julkaisua");
        setWidth("min(40em, 95vw)");

        TextField species = new TextField("Kalalaji");
        species.setRequired(true);
        species.setPlaceholder("esim. hauki");
        NumberField weight = number("Paino (kg)");
        NumberField length = number("Pituus (cm)");
        NumberField girth = number("Ympärysmitta (cm)");
        TextArea description = new TextArea("Kuvaus");
        description.setMaxLength(2000);

        TextField location = new TextField("Paikka");
        location.setPlaceholder("esim. Päijänne, Lahti");
        Button find = new Button("Hae kartalta", VaadinIcon.SEARCH.create(), e -> searchPlace(location.getValue()));
        find.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        location.setSuffixComponent(find);
        location.addKeyDownListener(Key.ENTER, e -> searchPlace(location.getValue()));

        map = mapConfig.newMap();
        map.setWidthFull();
        map.setHeight("16em");
        map.addMapClickListener(e -> setPin(e.getCoordinate().y, e.getCoordinate().x));
        clearPin.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        clearPin.setEnabled(false);
        pinInfo.getStyle().set("opacity", "0.8").set("font-size", "0.9em");

        Upload upload = new Upload(UploadHandler.inMemory((meta, data) -> {
            photo = data;
            photoType = meta.contentType();
        }));
        upload.setAcceptedFileTypes("image/jpeg", "image/png", "image/gif", "image/webp");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024);
        upload.setDropLabel(new Span("Pudota kuva tähän"));
        upload.setUploadButton(new Button("Valitse kuva"));
        upload.addFileRemovedListener(e -> photo = null);

        // Aika: ehdotetaan aina tämän päivän päivää ja kellonaikaa; muokatessa tallennettu aika
        ZoneId zone = ZoneId.systemDefault();
        DateTimePicker caughtAt = new DateTimePicker("Saaliin aika");
        caughtAt.setLocale(Locale.of("fi", "FI"));
        caughtAt.setStep(Duration.ofMinutes(1));
        caughtAt.setMax(LocalDateTime.now(zone).plusMinutes(5));
        caughtAt.setHelperText(existing == null
                ? "Oletuksena tämä hetki. Muuta, jos kala on pyydetty aiemmin."
                : "Voit muuttaa päivämäärää ja aikaa.");
        caughtAt.setValue(existing == null
                ? LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES)
                : LocalDateTime.ofInstant(existing.getCaughtAt(), zone).truncatedTo(ChronoUnit.MINUTES));

        Checkbox removePhoto = new Checkbox("Poista nykyinen kuva");
        if (existing != null) {
            species.setValue(existing.getSpecies());
            weight.setValue(existing.getWeightKg());
            length.setValue(existing.getLengthCm());
            girth.setValue(existing.getGirthCm());
            location.setValue(existing.getLocation() == null ? "" : existing.getLocation());
            description.setValue(existing.getDescription() == null ? "" : existing.getDescription());
            if (existing.hasCoordinates()) {
                map.setCenter(existing.getLongitude(), existing.getLatitude());
                map.setZoomLevel(9);
                setPin(existing.getLatitude(), existing.getLongitude());
            }
        }

        FormLayout form = new FormLayout(species, caughtAt, weight, length, girth, location, map, pinInfo,
                clearPin, description, upload);
        form.setColspan(caughtAt, 2);
        if (existing != null && existing.getPhotoFile() != null) {
            upload.setDropLabel(new Span("Valitse uusi kuva korvataksesi nykyisen"));
            form.add(removePhoto);
            form.setColspan(removePhoto, 2);
        }
        form.setColspan(location, 2);
        form.setColspan(map, 2);
        form.setColspan(pinInfo, 2);
        form.setColspan(clearPin, 2);
        form.setColspan(description, 2);
        form.setColspan(upload, 2);
        add(form);

        Button save = new Button(existing == null ? "Julkaise" : "Tallenna", e -> {
            if (species.isEmpty()) {
                species.setInvalid(true);
                species.setErrorMessage("Kalalaji vaaditaan");
                return;
            }
            if (caughtAt.isEmpty()) {
                caughtAt.setInvalid(true);
                caughtAt.setErrorMessage("Anna saaliin päivämäärä ja aika");
                return;
            }
            Instant when = caughtAt.getValue().atZone(zone).toInstant();
            // Jos paikka on kirjoitettu mutta pistettä ei ole valittu, yritetään paikantaa nimellä
            if (latitude == null && autoLocate && !location.isEmpty()) {
                geocoder.search(location.getValue()).ifPresent(p -> {
                    latitude = p.latitude();
                    longitude = p.longitude();
                });
            }
            try {
                if (existing == null) {
                    posts.create(currentUser.get(), species.getValue(), weight.getValue(), length.getValue(),
                            girth.getValue(), location.getValue(), description.getValue(), photo, photoType,
                            latitude, longitude, when);
                } else {
                    posts.update(existing.getId(), currentUser.get(), species.getValue(), weight.getValue(),
                            length.getValue(), girth.getValue(), location.getValue(), description.getValue(),
                            photo, photoType, removePhoto.getValue(), latitude, longitude, when);
                }
                close();
                onSaved.run();
            } catch (SecurityException ex) {
                Notification.show("Et voi muokata tätä julkaisua").addThemeVariants(NotificationVariant.ERROR);
                close();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        getFooter().add(new Button("Peruuta", e -> close()), save);
    }

    /** Hakee paikan nimellä, asettaa pisteen sen kohdalle ja kohdistaa kartan siihen. */
    void searchPlace(String query) {
        if (query == null || query.isBlank()) {
            Notification.show("Kirjoita ensin paikan nimi").addThemeVariants(NotificationVariant.WARNING);
            return;
        }
        var place = geocoder.search(query);
        if (place.isEmpty()) {
            Notification.show("Paikkaa ei löytynyt. Valitse piste kartalta.")
                    .addThemeVariants(NotificationVariant.WARNING);
            return;
        }
        var p = place.get();
        setPin(p.latitude(), p.longitude());
        pinInfo.setText("📍 " + p.name());
        map.flyTo(p.longitude(), p.latitude(), PLACE_ZOOM);
    }

    /** Asettaa julkaisun pisteen (kartan klikkaus tai paikkahaku). Vanha piste korvataan. */
    void setPin(double lat, double lon) {
        if (pin != null) pin.remove();
        pin = map.addMarker(lon, lat);
        latitude = lat;
        longitude = lon;
        pinInfo.setText(String.format(Locale.ROOT, "📍 %.4f, %.4f", lat, lon));
        clearPin.setEnabled(true);
    }

    void clearPin() {
        autoLocate = false;
        if (pin != null) pin.remove();
        pin = null;
        latitude = null;
        longitude = null;
        pinInfo.setText("Ei pistettä kartalla. Klikkaa karttaa tai hae paikka nimellä.");
        clearPin.setEnabled(false);
    }

    Double pinLatitude() {
        return latitude;
    }

    Double pinLongitude() {
        return longitude;
    }

    private static NumberField number(String label) {
        NumberField f = new NumberField(label);
        f.setMin(0);
        f.setStep(0.1);
        return f;
    }
}
