package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.*;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

public final class NightLightingPolicy extends ManagedLightingPolicy {
    private final Toggle enabled;
    private final OccupancySensor occupancy;
    private final GroupAddress sceneGroup, offGroup;
    private final int scene;
    private Instant lastOccupied;
    private boolean sceneTriggered, darkened, morningLockout, wasEnabled;
    private long generation = -1;

    public NightLightingPolicy(String name, KNXAdapter adapter, Toggle enabled, OccupancySensor occupancy,
                               GroupAddress sceneGroup, int scene, GroupAddress offGroup) {
        super(name, adapter, enabled.getLocation(), PolicyKind.NIGHT_LIGHT);
        if (scene < 0 || scene > 63) throw new IllegalArgumentException("Scene must be 0..63");
        this.enabled = enabled; this.occupancy = occupancy; this.sceneGroup = sceneGroup;
        this.scene = scene; this.offGroup = offGroup;
        adapter.declarePolicyCommand(name, enabled.getLocation(), "scene", sceneGroup, "18.001");
        adapter.declarePolicyCommand(name, enabled.getLocation(), "off", offGroup, "1.001");
    }
    @Override protected Duration interval() { return Duration.ofSeconds(5); }
    @Override protected void started() { wasEnabled = enabled.isOn(); generation = adapter.generation(); }
    @Override protected void handle(EntityEvent event) throws Exception {
        sessionChanged();
        if (!(event instanceof StateChangedEvent state)) return;
        if (state.entityId().equals(enabled.getNamedId())) {
            if (!enabled.isOn()) { reset(); wasEnabled = false; return; }
            if (!wasEnabled) {
                reset(); wasEnabled = true;
                adapter.bus().write(offGroup, false);
                darkened = true;
                if (occupancy.isStateKnown() && occupancy.getState()) motion();
            }
        } else if (enabled.isOn() && state.entityId().equals(occupancy.getNamedId())
                && Boolean.TRUE.equals(state.value())) motion();
    }
    @Override protected void evaluate() throws Exception {
        sessionChanged();
        if (!enabled.isOn()) { reset(); wasEnabled = false; return; }
        wasEnabled = true;
        int hour = LocalTime.now(adapter.clock()).getHour();
        // Must run before the morning lockout return; otherwise the 09:00 off is unreachable.
        if (hour == 9) { enabled.setAutomationState(false); return; }
        if (hour == 5 && !morningLockout) {
            adapter.bus().write(offGroup, false);
            darkened = true; sceneTriggered = false; morningLockout = true;
        }
        if (morningLockout || !occupancy.isStateKnown()) return;
        if (occupancy.getState()) lastOccupied = adapter.clock().instant();
        if (!occupancy.getState() && lastOccupied != null && !darkened
                && Duration.between(lastOccupied, adapter.clock().instant()).getSeconds() >= 300) {
            adapter.bus().write(offGroup, false);
            darkened = true; sceneTriggered = false;
        }
    }
    private void sessionChanged() {
        if (generation == adapter.generation()) return;
        generation = adapter.generation();
        lastOccupied = null; sceneTriggered = false; darkened = false;
        // Reconnecting must not lift a morning lockout already reached in this process.
    }
    private void motion() throws Exception {
        if (morningLockout) return;
        lastOccupied = adapter.clock().instant(); darkened = false;
        if (!sceneTriggered) {
            adapter.bus().write(sceneGroup, scene, ProcessCommunication.UNSCALED);
            sceneTriggered = true;
        }
    }
    private void reset() { lastOccupied = null; sceneTriggered = false; darkened = false; morningLockout = false; }
}
