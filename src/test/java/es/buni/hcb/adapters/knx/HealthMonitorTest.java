package es.buni.hcb.adapters.knx;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class HealthMonitorTest {
    @Test void anyTelegramKeepsTheReceivePathAlive() {
        var now = new AtomicLong(); var reconnects = new AtomicInteger();
        var monitor = new HealthMonitor(reason -> reconnects.incrementAndGet(), () -> true, now::get, Duration.ofSeconds(45));
        now.set(Duration.ofSeconds(40).toNanos()); monitor.receivedTelegram();
        now.set(Duration.ofSeconds(84).toNanos()); monitor.checkHealth(); assertEquals(0, reconnects.get());
        now.set(Duration.ofSeconds(85).toNanos()); monitor.checkHealth(); assertEquals(1, reconnects.get());
    }
    @Test void startupAndSynchronizationHaveGraceAndUseMonotonicTime() {
        var now = new AtomicLong(); var ready = new AtomicBoolean(); var reconnects = new AtomicInteger();
        var monitor = new HealthMonitor(reason -> reconnects.incrementAndGet(), ready::get, now::get, Duration.ofSeconds(45));
        now.set(Duration.ofMinutes(10).toNanos()); monitor.checkHealth(); assertEquals(0, reconnects.get());
        monitor.reset(); ready.set(true); monitor.checkHealth(); assertEquals(0, reconnects.get());
        now.addAndGet(Duration.ofSeconds(45).toNanos()); monitor.checkHealth(); assertEquals(1, reconnects.get());
    }
}
