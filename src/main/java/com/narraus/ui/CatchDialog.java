package com.narraus.ui;

import com.narraus.domain.PostService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;

/** Lomake uuden saaliin lisäämiseen. */
class CatchDialog extends Dialog {

    private byte[] photo;
    private String photoType;

    CatchDialog(PostService posts, CurrentUser currentUser, Runnable onSaved) {
        setHeaderTitle("Uusi saalis");
        setWidth("min(36em, 95vw)");

        TextField species = new TextField("Kalalaji");
        species.setRequired(true);
        species.setPlaceholder("esim. hauki");
        NumberField weight = number("Paino (kg)");
        NumberField length = number("Pituus (cm)");
        NumberField girth = number("Ympärysmitta (cm)");
        TextField location = new TextField("Paikka");
        TextArea description = new TextArea("Kuvaus");
        description.setMaxLength(2000);

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

        FormLayout form = new FormLayout(species, location, weight, length, girth, description, upload);
        form.setColspan(description, 2);
        form.setColspan(upload, 2);
        add(form);

        Button save = new Button("Julkaise", e -> {
            if (species.isEmpty()) {
                species.setInvalid(true);
                species.setErrorMessage("Kalalaji vaaditaan");
                return;
            }
            try {
                posts.create(currentUser.get(), species.getValue(), weight.getValue(), length.getValue(),
                        girth.getValue(), location.getValue(), description.getValue(), photo, photoType);
                close();
                onSaved.run();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage()).addThemeVariants(NotificationVariant.ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        getFooter().add(new Button("Peruuta", e -> close()), save);
    }

    private static NumberField number(String label) {
        NumberField f = new NumberField(label);
        f.setMin(0);
        f.setStep(0.1);
        return f;
    }
}
