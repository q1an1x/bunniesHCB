package es.buni.hcb.core.events;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

public class EventBus {

    private final Set<Consumer<EntityEvent>> listeners =
            new CopyOnWriteArraySet<>();

    public void publish(EntityEvent event) {
        for (Consumer<EntityEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    public void subscribe(Consumer<EntityEvent> listener) {
        listeners.add(listener);
    }
}
