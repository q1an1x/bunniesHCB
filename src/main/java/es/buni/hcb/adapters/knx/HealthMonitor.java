package es.buni.hcb.adapters.knx;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * The installation emits online telegrams every 15 seconds. Any received telegram
 * proves the receive path is alive; silence for 45 seconds requests reconnection.
 * This deliberately does not diagnose individual sensors or require a special GA.
 */
public final class HealthMonitor {
    private final Reconnectable adapter;
    private final BooleanSupplier ready;
    private final LongSupplier nanoTime;
    private final long silenceNanos;
    private volatile long lastSeen;
    private ScheduledExecutorService scheduler;
    private boolean closed;

    public HealthMonitor(KNXAdapter adapter) {
        this(adapter, adapter::isReady, System::nanoTime, adapter.settings().silenceTimeout());
    }

    HealthMonitor(Reconnectable adapter, BooleanSupplier ready, LongSupplier nanoTime, Duration silence) {
        if (silence.isZero() || silence.isNegative()) throw new IllegalArgumentException("silence <= 0");
        this.adapter = adapter;
        this.ready = ready;
        this.nanoTime = nanoTime;
        this.silenceNanos = silence.toNanos();
        reset();
    }

    public synchronized void start() {
        if (closed || scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("knx-health").factory());
        scheduler.scheduleWithFixedDelay(this::checkHealth, 5, 5, TimeUnit.SECONDS);
    }

    public void reset() { lastSeen = nanoTime.getAsLong(); }
    public void receivedTelegram() { lastSeen = nanoTime.getAsLong(); }
    public synchronized void shutdown() { closed = true; if (scheduler != null) scheduler.shutdownNow(); }

    void checkHealth() {
        // Synchronization/startup is excluded, then reset() grants a full silence window.
        if (ready.getAsBoolean() && nanoTime.getAsLong() - lastSeen >= silenceNanos) {
            adapter.reconnect("no received telegrams for " + Duration.ofNanos(silenceNanos).toSeconds() + "s");
        }
    }
}
