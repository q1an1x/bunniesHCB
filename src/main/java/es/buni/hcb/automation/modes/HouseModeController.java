package es.buni.hcb.automation.modes;

import com.google.gson.Gson;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.*;
import es.buni.hcb.automation.PolicyKind;
import es.buni.hcb.config.KNXEntities;
import es.buni.hcb.config.knx.ScenesEnum;
import es.buni.hcb.core.Lifecycle;
import es.buni.hcb.utils.Logger;
import es.buni.hcb.utils.PrivateFiles;
import io.calimero.process.ProcessCommunication;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Coordinates explicit household intent; never attempts to roll back physical actions. */
public final class HouseModeController implements Lifecycle {
    public enum Phase { ACTIVE, APPLYING, PAUSED, FAILED }
    public record Status(HouseMode mode, Phase phase, String detail) { }
    private record Owned(boolean before, boolean requested, long generation, long manualRevision) { }
    private final KNXAdapter adapter;
    private final ModeCatalog catalog;
    private final Map<String, Owned> owned = new LinkedHashMap<>();
    private final Set<CompletableFuture<Status>> pending = ConcurrentHashMap.newKeySet();
    private final Semaphore queueSlots = new Semaphore(8);
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();
    private volatile Status status = new Status(HouseMode.HOME, Phase.ACTIVE, "No mode selected");
    private volatile boolean running;
    private final java.util.concurrent.atomic.AtomicLong selectionRevision = new java.util.concurrent.atomic.AtomicLong();
    private Path storage;

    public HouseModeController(KNXAdapter adapter) {
        this.adapter = adapter;
        catalog = new ModeCatalog(adapter);
        adapter.declareCommand("house.modes", "scene", KNXEntities.SCENE_RECALL_GROUP_ADDRESS, "18.001");
    }

    public synchronized void setStorage(Path storage) {
        if (running) throw new IllegalStateException("Configure mode storage before startup");
        this.storage = storage;
    }

    @Override public synchronized void start() {
        if (running) return;
        // A saved mode is intent, not a durable queue of commands. Require an explicit
        // selection after restart; never replay its lighting actions during startup.
        if (storage != null && Files.exists(storage, LinkOption.NOFOLLOW_LINKS)) {
            try {
                if (Files.isSymbolicLink(storage) || Files.size(storage) > 4096)
                    throw new IllegalStateException("Invalid house mode checkpoint");
                Status previous = new Gson().fromJson(Files.readString(storage), Status.class);
                if (previous == null || previous.mode() == null || previous.phase() == null)
                    throw new IllegalStateException("Incomplete house mode checkpoint");
                status = previous.mode() == HouseMode.HOME && previous.phase() == Phase.ACTIVE
                        ? new Status(HouseMode.HOME, Phase.ACTIVE, "Daily mode; room state will be synchronized")
                        : new Status(previous.mode(), Phase.PAUSED, "Select a mode explicitly after process restart");
            } catch (Exception e) {
                status = new Status(HouseMode.HOME, Phase.FAILED, "Mode checkpoint cannot be read");
                Logger.error("House mode checkpoint cannot be read; automation is paused", e);
            }
        }
        running = true;
    }

    @Override public synchronized void stop() {
        running = false;
        pending.forEach(f -> f.completeExceptionally(new IllegalStateException("Controller stopped")));
        callbacks.clear();
    }

    public Status status() { return status; }
    public void connectionLost() {
        pending.forEach(f -> f.completeExceptionally(new IllegalStateException("KNX session ended; mode request not replayed")));
    }
    public long selectionRevision() { return selectionRevision.get(); }
    public void onChange(Runnable callback) { callbacks.add(callback); }

    public boolean permits(String room, PolicyKind kind) {
        Status current = status;
        return current.phase() == Phase.ACTIVE && catalog.permits(current.mode(), room, kind);
    }

    /** No side effects: also used by the offline CLI and tests. */
    public synchronized ModePlan preview(HouseMode mode) {
        Map<String, Boolean> desired = catalog.automationSettings(mode);
        var actions = new ArrayList<ModeAction>();
        var warnings = new ArrayList<String>();
        for (var entry : owned.entrySet()) {
            if (desired.containsKey(entry.getKey())) continue;
            Toggle toggle = toggle(entry.getKey());
            if (restorable(toggle, entry.getValue())) {
                actions.add(new ModeAction(ModeAction.Kind.AUTOMATION, entry.getKey(), entry.getValue().before() ? 1 : 0));
            } else warnings.add("Preserve newer/unknown automation setting: " + entry.getKey());
        }
        desired.forEach((id, value) -> {
            if (!toggle(id).isStateKnown()) warnings.add("Original state unknown; will not invent a restoration value: " + id);
            actions.add(new ModeAction(ModeAction.Kind.AUTOMATION, id, value ? 1 : 0));
        });
        actions.addAll(catalog.lightingActions(mode));
        if (mode == HouseMode.HOME) warnings.add("Restores only owned automation settings, never previous lamp power or brightness");
        return new ModePlan(mode, actions, warnings);
    }

    public CompletableFuture<Status> select(HouseMode mode) {
        Objects.requireNonNull(mode);
        if (!running || !adapter.isAutomationReady())
            return CompletableFuture.failedFuture(new IllegalStateException("House modes require live mode and enabled automations"));
        if (!queueSlots.tryAcquire()) return CompletableFuture.failedFuture(new IllegalStateException("Too many mode requests"));
        long epoch = adapter.generation();
        var result = new CompletableFuture<Status>();
        pending.add(result);
        result.whenComplete((value, failure) -> {
            pending.remove(result); queueSlots.release();
            if (failure != null) notifyChanged();
        });
        result.orTimeout(30, TimeUnit.SECONDS);
        try {
            adapter.getRegistry().getEventBus().execute(() -> {
                if (epoch != adapter.generation()) {
                    result.completeExceptionally(new IllegalStateException("Mode request expired with its KNX connection"));
                    return;
                }
                adapter.runInSession(epoch, () -> apply(mode, epoch, result));
            });
        } catch (RuntimeException e) { result.completeExceptionally(e); }
        return result;
    }

    private synchronized void apply(HouseMode mode, long epoch, CompletableFuture<Status> result) {
        if (result.isDone()) return;
        if (status.mode() == mode && status.phase() == Phase.ACTIVE && mode != HouseMode.HOME) {
            result.complete(status); return; // Repeated ON is idempotent; it is not a scene replay.
        }
        try {
            ModePlan plan = preview(mode);
            for (ModeAction action : plan.actions()) validate(action);
            var desired = catalog.automationSettings(mode);
            selectionRevision.incrementAndGet();
            checkpoint(new Status(mode, Phase.APPLYING, "Applying explicit mode selection"));
            notifyChanged();
            for (String room : catalog.affectedRooms(mode)) adapter.manualOverrides().resume(room);
            owned.entrySet().removeIf(entry -> !restorable(toggle(entry.getKey()), entry.getValue()));
            for (ModeAction action : plan.actions()) {
                if (!running || result.isDone() || epoch != adapter.generation() || !adapter.isAutomationReady())
                    throw new IllegalStateException("Mode transition interrupted; completed actions were not rolled back");
                if (action.kind() == ModeAction.Kind.AUTOMATION) applyAutomation(action, desired, epoch);
                else execute(action);
            }
            owned.keySet().removeIf(id -> !desired.containsKey(id));
            checkpoint(new Status(mode, Phase.ACTIVE, "Mode intent applied; actuator state remains feedback-driven"));
            result.complete(status);
        } catch (Exception e) {
            status = new Status(mode, Phase.FAILED, "Partial/failed transition; choose a mode explicitly to recover");
            try { checkpoint(status); } catch (Exception saveFailure) { e.addSuppressed(saveFailure); }
            result.completeExceptionally(e);
            Logger.error("House mode transition failed; no automatic rollback or replay", e);
        } finally { notifyChanged(); }
    }

    private void applyAutomation(ModeAction action, Map<String, Boolean> desired, long epoch) throws Exception {
        Toggle toggle = toggle(action.target());
        boolean value = action.value() != 0;
        Owned previous = owned.get(action.target());
        if (!desired.containsKey(action.target()) && (previous == null || !restorable(toggle, previous))) {
            owned.remove(action.target()); return;
        }
        if (desired.containsKey(action.target()) && previous == null && toggle.isStateKnown() && toggle.isOn() != value)
            owned.put(action.target(), new Owned(toggle.isOn(), value, epoch, toggle.manualRevision()));
        else if (previous != null && desired.containsKey(action.target()))
            owned.put(action.target(), new Owned(previous.before(), value, epoch, previous.manualRevision()));
        // Even a repeated OFF is explicit ownership and cancels an older scene-restoration timer.
        toggle.setModeState(value);
        if (!desired.containsKey(action.target())) owned.remove(action.target());
    }

    private boolean restorable(Toggle toggle, Owned previous) {
        return previous.generation() == adapter.generation() && previous.manualRevision() == toggle.manualRevision()
                && toggle.isStateKnown() && toggle.isOn() == previous.requested();
    }
    private Toggle toggle(String id) { return Toggle.class.cast(Objects.requireNonNull(adapter.getRegistry().get(id), id)); }

    private void validate(ModeAction action) {
        switch (action.kind()) {
            case AUTOMATION -> toggle(action.target());
            case POWER -> Light.class.cast(Objects.requireNonNull(adapter.getRegistry().get(action.target()), action.target()));
            case BRIGHTNESS -> Dimmable.class.cast(Objects.requireNonNull(adapter.getRegistry().get(action.target()), action.target()));
            case COLOR_TEMPERATURE -> Tunable.class.cast(Objects.requireNonNull(adapter.getRegistry().get(action.target()), action.target()));
            case SCENE -> {
                if (ScenesEnum.valueOf(action.target()).getSceneNumber() != action.value()) throw new IllegalArgumentException("Scene mapping changed");
            }
        }
    }

    private void execute(ModeAction action) throws Exception {
        switch (action.kind()) {
            case POWER -> {
                Light light = (Light) adapter.getRegistry().get(action.target());
                if (action.value() != 0) light.on(); else light.off();
            }
            case BRIGHTNESS -> ((Dimmable) adapter.getRegistry().get(action.target())).setBrightnessValue(action.value());
            case COLOR_TEMPERATURE -> ((Tunable) adapter.getRegistry().get(action.target())).setColorTemperatureValue(action.value());
            case SCENE -> adapter.bus().write(KNXEntities.SCENE_RECALL_GROUP_ADDRESS, action.value(), ProcessCommunication.UNSCALED);
            case AUTOMATION -> throw new IllegalStateException("Automation ownership must be tracked");
        }
    }

    private void checkpoint(Status next) throws Exception {
        // Publish the gate before disk I/O: persistence failure also leaves automation paused.
        status = next;
        if (storage != null) PrivateFiles.writeAtomically(storage, new Gson().toJson(next).getBytes(StandardCharsets.UTF_8));
    }
    private void notifyChanged() {
        for (Runnable callback : callbacks) try { callback.run(); }
        catch (RuntimeException e) { Logger.error("House mode subscriber failed", e); }
    }
}
