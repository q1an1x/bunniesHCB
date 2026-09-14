package es.buni.hcb.core.events;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class EventBusTest {
    @Test void failedSubscriberDoesNotBreakDeliveryAndSubscriptionsAreRemovable() {
        var bus = new EventBus(); var received = new AtomicInteger();
        bus.subscribe(e -> { throw new IllegalStateException("broken policy"); });
        Consumer<EntityEvent> listener = e -> received.incrementAndGet(); bus.subscribe(listener); bus.subscribe(listener);
        bus.publish(StateChangedEvent.of("sensor",true)); bus.barrier().join(); assertEquals(1,received.get());
        bus.unsubscribe(listener); bus.publish(StateChangedEvent.of("sensor",false)); bus.barrier().join(); assertEquals(1,received.get()); bus.close();
    }
}
