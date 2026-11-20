package com.narraus.ui;

import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

/** Ilmoitus siitä, että käyttäjän lukemattomien määrä on voinut muuttua (esim. keskustelu luettiin). */
class UnreadChanged extends ComponentEvent<UI> {

    UnreadChanged(UI source) {
        super(source, false);
    }

    static void fire(UI ui) {
        ComponentUtil.fireEvent(ui, new UnreadChanged(ui));
    }
}
