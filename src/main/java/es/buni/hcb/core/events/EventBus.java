package es.buni.hcb.core.events;

import es.buni.hcb.utils.Logger;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Serialized, bounded event delivery. Listeners never run inside a device setter's lock. */
public final class EventBus implements AutoCloseable {
    private final Set<Consumer<EntityEvent>> listeners = new CopyOnWriteArraySet<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(512), Thread.ofPlatform().daemon().name("hcb-events").factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private volatile Runnable overflow = () -> { };

    public void publish(EntityEvent event) {
        publish(event, Runnable::run);
    }
    /** Allows a transport to retain its session context throughout listener delivery. */
    public void publish(EntityEvent event, Consumer<Runnable> context) {
        execute(() -> context.accept(() -> {
            for (Consumer<EntityEvent> listener : listeners) {
                try { listener.accept(event); }
                catch (RuntimeException e) { Logger.error("Event subscriber failed for " + event.entityId(), e); }
            }
        }));
    }
    public void execute(Runnable action) {
        try { executor.execute(action); }
        catch (RejectedExecutionException e) {
            if (!executor.isShutdown()) { overflow.run(); throw e; }
        }
    }
    public CompletableFuture<Void> barrier() {
        var result = new CompletableFuture<Void>();
        if (executor.isShutdown()) result.complete(null);
        else execute(() -> result.complete(null));
        return result;
    }
    public void onOverflow(Runnable handler) { overflow = handler; }
    public void clearPending() { executor.getQueue().clear(); }
    public void subscribe(Consumer<EntityEvent> listener) { listeners.add(listener); }
    public void unsubscribe(Consumer<EntityEvent> listener) { listeners.remove(listener); }
    public int listenerCount() { return listeners.size(); }
    @Override public void close() { listeners.clear(); executor.shutdownNow(); }
}
