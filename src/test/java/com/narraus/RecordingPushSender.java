package com.narraus;

import com.narraus.domain.PushSender;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Testien lähetin: ei kutsu oikeaa push-palvelua, vaan tallentaa lähetetyt viestit. */
public class RecordingPushSender implements PushSender {

    public record Sent(String endpoint, String title, String body) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private final Map<String, Result> results = new ConcurrentHashMap<>();

    @Override
    public Result send(String endpoint, String p256dh, String auth, String title, String body) {
        sent.add(new Sent(endpoint, title, body));
        return results.getOrDefault(endpoint, Result.OK);
    }

    /** Kaikki lähetetyt viestit annetulle laitteelle. */
    public List<Sent> sentTo(String endpoint) {
        return sent.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
    }

    /** Määrää, mitä tietty laite vastaa (esim. GONE, kun selain on peruuttanut tilauksen). */
    public void respondWith(String endpoint, Result result) {
        results.put(endpoint, result);
    }
}
