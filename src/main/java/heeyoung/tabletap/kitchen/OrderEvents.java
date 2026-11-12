package heeyoung.tabletap.kitchen;

import heeyoung.tabletap.order.OrderChanged;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes order changes to the kitchen pages of the restaurant they belong to.
 * Emitters live in this instance's memory, so the app runs as one instance.
 */
@Component
public class OrderEvents {

    static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<UUID, Set<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Set<SseEmitter> emitters = emittersByTenant.computeIfAbsent(tenantId, id -> ConcurrentHashMap.newKeySet());
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        // Sends the response headers now, so the browser reports the stream as open.
        send(emitters, emitter, SseEmitter.event().comment("connected"));
        return emitter;
    }

    // After commit, so the kitchen never sees an order that was rolled back.
    @TransactionalEventListener
    void publish(OrderChanged change) {
        Set<SseEmitter> emitters = emittersByTenant.getOrDefault(change.tenantId(), Set.of());
        for (SseEmitter emitter : emitters) {
            send(emitters, emitter, SseEmitter.event().name("order").data(change.order()));
        }
    }

    // Proxies close idle connections, and a send is the only way to notice a
    // client that went away without saying so.
    @Scheduled(fixedRate = 25_000)
    void heartbeat() {
        emittersByTenant.values().forEach(emitters ->
                emitters.forEach(emitter -> send(emitters, emitter, SseEmitter.event().comment("ping"))));
    }

    int subscriberCount(UUID tenantId) {
        return emittersByTenant.getOrDefault(tenantId, Set.of()).size();
    }

    private static void send(Set<SseEmitter> emitters, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
    }
}
