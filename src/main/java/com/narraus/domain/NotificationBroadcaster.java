package com.narraus.domain;

import com.vaadin.flow.shared.Registration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Kertoo avoimille selainistunnoille, että käyttäjälle tuli uusi ilmoitus (parametrina vastaanottajan id). */
@Component
public class NotificationBroadcaster {
    private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Consumer<Long> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void broadcast(Long recipientId) {
        Runnable send = () -> listeners.forEach(l -> l.accept(recipientId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
