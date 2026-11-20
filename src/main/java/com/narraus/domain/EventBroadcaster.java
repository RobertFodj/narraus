package com.narraus.domain;

import com.vaadin.flow.shared.Registration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Kertää avoimille selainistunnoille, että kilpailun keskustelussa tai tuloksissa tapahtui jotain. */
@Component
public class EventBroadcaster {
    private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Consumer<Long> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void broadcast(Long eventId) {
        listeners.forEach(l -> l.accept(eventId));
    }
}
