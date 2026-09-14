package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.config.knx.*;
import es.buni.hcb.core.Lifecycle;
import es.buni.hcb.core.events.*;
import es.buni.hcb.utils.Logger;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class SceneAutomationManager implements Lifecycle, Consumer<EntityEvent> {
    private final KNXAdapter adapter;
    private final Duration suspension;
    private final Map<String, ScheduledFuture<?>> timers = new HashMap<>();
    private final Map<String, Long> revisions = new HashMap<>();
    private long nextRevision;
    private long generation;
    private boolean running;

    public SceneAutomationManager(KNXAdapter adapter) { this(adapter, Duration.ofHours(8)); }
    public SceneAutomationManager(KNXAdapter adapter, Duration suspension) {
        this.adapter = adapter; this.suspension = suspension;
    }
    @Override public synchronized void start() {
        if (running) return;
        running = true;
        generation = adapter.generation();
        adapter.getRegistry().getEventBus().subscribe(this);
    }
    @Override public synchronized void stop() {
        running = false;
        adapter.getRegistry().getEventBus().unsubscribe(this);
        timers.values().forEach(t -> t.cancel(false));
        timers.clear();
        revisions.clear();
    }
    @Override public synchronized void accept(EntityEvent event) {
        if (!running || !adapter.isAutomationReady()) return;
        if (generation != adapter.generation()) {
            timers.values().forEach(t -> t.cancel(false)); timers.clear(); revisions.clear();
            generation = adapter.generation();
        }
        if (event instanceof SceneRecalledEvent recall) {
            ScenesEnum scene = ScenesEnum.fromNumber(recall.sceneId());
            if (scene != null) for (AutomationType type : scene.getDisabledAutomations()) suspend(scene.getLocation(), type);
        } else if (event instanceof StateChangedEvent state
                && state.origin() != StateChangedEvent.Origin.AUTOMATION
                && state.origin() != StateChangedEvent.Origin.BUS_RESPONSE
                && state.origin() != StateChangedEvent.Origin.BUS_ECHO) {
            // An explicit manual setting, including a repeated OFF, cancels automatic restoration.
            cancel(state.entityId());
        }
    }
    private void cancel(String key) {
        revisions.remove(key);
        var timer = timers.remove(key);
        if (timer != null) timer.cancel(false);
    }
    private void suspend(String location, AutomationType type) {
        String key = location + ".toggle." + type.getKeySuffix();
        if (!(adapter.getRegistry().get(key) instanceof Toggle toggle)) return;
        if (!toggle.isOn() && !timers.containsKey(key)) return;
        try {
            if (toggle.isOn()) {
                toggle.setAutomationState(false);
            }
            cancel(key);
            if (type == AutomationType.NIGHT) return;
            long epoch = adapter.generation();
            long revision = ++nextRevision;
            revisions.put(key, revision);
            timers.put(key, adapter.scheduler().schedule(() -> adapter.getRegistry().getEventBus().execute(() -> restore(key, toggle, epoch, revision)),
                    suspension.toMillis(), TimeUnit.MILLISECONDS));
        } catch (Exception e) { Logger.error("Unable to suspend " + key, e); }
    }
    private synchronized void restore(String key, Toggle toggle, long epoch, long revision) {
        if (!Objects.equals(revisions.get(key), revision)) return;
        revisions.remove(key);
        timers.remove(key);
        // Never replay an old timer into a new connection or overwrite unknown state.
        if (!running || !adapter.isAutomationReady() || adapter.generation() != epoch || !toggle.isStateKnown()) return;
        try {
            toggle.setAutomationState(true);
        } catch (Exception e) { Logger.error("Unable to restore " + key, e); }
    }
}
