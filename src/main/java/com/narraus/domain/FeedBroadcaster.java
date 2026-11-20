package com.narraus.domain;

import com.vaadin.flow.shared.Registration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Kertaa avoimille selainistunnoille, että syötteeseen on tullut uusi julkaisu. */
@Component
public class FeedBroadcaster {
    private final List<Consumer<Long>> listeners = new CopyOnWriteArrayList<>();

    public Registration register(Consumer<Long> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Lähettää tiedon vasta transaktion commitin jälkeen, jotta kuuntelijat näkevät uuden rivin. */
    void broadcast(Long postId) {
        Runnable send = () -> listeners.forEach(l -> l.accept(postId));
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
