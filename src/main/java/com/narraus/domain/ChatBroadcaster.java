package com.narraus.domain;

import com.vaadin.flow.shared.Registration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Välittää "keskustelussa X tapahtui jotain" -tiedon avoimille selainistunnoille. */
@Component
public class ChatBroadcaster {
    private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Consumer<Long> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void broadcast(Long conversationId) {
        listeners.forEach(l -> l.accept(conversationId));
    }
}
