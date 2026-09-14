package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.core.events.*;
import es.buni.hcb.utils.Logger;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** A policy owns one subscription and at most one timer, independent of transport reconnects. */
public abstract class ManagedLightingPolicy implements LightingPolicy, Consumer<EntityEvent> {
    protected final String name;
    protected final KNXAdapter adapter;
    private final String room;
    private final PolicyKind kind;
    private boolean running;
    private boolean blocked;
    private ScheduledFuture<?> tick;

    protected ManagedLightingPolicy(String name, KNXAdapter adapter, String room, PolicyKind kind) {
        this.name = name; this.adapter = adapter; this.room = room; this.kind = kind;
    }
    protected Duration interval() { return null; }
    protected void started() { }
    protected void resumed() { }
    protected abstract void evaluate() throws Exception;
    protected void handle(EntityEvent event) throws Exception { }

    @Override public final synchronized void start() {
        if (running) return;
        running = true;
        started();
        adapter.getRegistry().getEventBus().subscribe(this);
        Duration interval = interval();
        if (interval != null) tick = adapter.scheduler().scheduleWithFixedDelay(() -> adapter.getRegistry().getEventBus().execute(this::update),
                interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }
    @Override public final synchronized void stop() {
        running = false;
        if (tick != null) tick.cancel(false);
        adapter.getRegistry().getEventBus().unsubscribe(this);
    }
    @Override public final synchronized void update() {
        if (!permitted()) return;
        try { evaluate(); } catch (Exception e) { Logger.error("[" + name + "] " + e.getMessage()); }
    }
    @Override public final synchronized void accept(EntityEvent event) {
        if (!permitted()) return;
        if (event instanceof StateChangedEvent state &&
                (state.origin() == StateChangedEvent.Origin.BUS_RESPONSE || state.origin() == StateChangedEvent.Origin.BUS_ECHO)) return;
        try { handle(event); } catch (Exception e) { Logger.error("[" + name + "] " + e.getMessage()); }
    }
    private boolean permitted() {
        if (!running || !adapter.isAutomationReady() || !adapter.permitsPolicy(room, kind)) { blocked = true; return false; }
        if (blocked) { blocked = false; resumed(); }
        return true;
    }
}
