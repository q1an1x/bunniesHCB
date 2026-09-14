package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Dimmable;
import es.buni.hcb.adapters.knx.entities.sensor.*;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.StateChangedEvent;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import java.time.Duration;
import java.time.Instant;

public final class ConstantLightingPolicy extends ManagedLightingPolicy {
    private final Toggle enabled, night;
    private final IlluminanceSensor lux;
    private final OccupancySensor occupancy;
    private final GroupAddress brightnessGroup, offGroup;
    private final Dimmable reference;
    private final double targetLux, deadband;
    private Instant lastOccupied;
    private Instant lastCommand;
    private int lastBrightness = -1;
    private boolean standby, offSent;
    private long generation = -1;

    public ConstantLightingPolicy(String name, KNXAdapter adapter, Toggle enabled, Toggle night,
            IlluminanceSensor lux, OccupancySensor occupancy, GroupAddress brightnessGroup,
            GroupAddress offGroup, Dimmable reference, double targetLux, double deadband) {
        super(name, adapter, enabled.getLocation(), PolicyKind.CONSTANT_LIGHT);
        if (!Double.isFinite(targetLux) || targetLux <= 0 || !Double.isFinite(deadband) || deadband <= 0)
            throw new IllegalArgumentException("Lighting target and deadband must be positive");
        this.enabled = enabled; this.night = night; this.lux = lux; this.occupancy = occupancy;
        this.brightnessGroup = brightnessGroup; this.offGroup = offGroup; this.reference = reference;
        this.targetLux = targetLux; this.deadband = deadband;
        adapter.declarePolicyCommand(name, enabled.getLocation(), "brightness", brightnessGroup, "5.001");
        adapter.declarePolicyCommand(name, enabled.getLocation(), "off", offGroup, "1.001");
    }
    @Override protected Duration interval() { return Duration.ofSeconds(1); }
    @Override protected void handle(EntityEvent event) throws Exception {
        if (event instanceof StateChangedEvent state && (state.entityId().equals(enabled.getNamedId())
                || state.entityId().equals(occupancy.getNamedId()) || state.entityId().equals(night.getNamedId()))) evaluate();
    }
    @Override protected void evaluate() throws Exception {
        if (!enabled.isOn() || night.isOn()) { reset(); return; }
        if (generation != adapter.generation()) { reset(); generation = adapter.generation(); }
        if (!occupancy.isStateKnown() || !reference.hasBrightnessState() || !night.isStateKnown()) return;
        Instant now = adapter.clock().instant();
        if (occupancy.getState()) { lastOccupied = now; standby = false; offSent = false; }
        if (lastOccupied == null) return; // Unknown/startup absence must never turn on a room.
        long vacantSeconds = Duration.between(lastOccupied, now).getSeconds();
        if (vacantSeconds >= 360) {
            if (!offSent) { adapter.bus().write(offGroup, false); offSent = true; }
            return;
        }
        if (vacantSeconds >= 300) {
            if (!standby) { writeBrightness(20, now); standby = true; }
            return;
        }
        if (!lux.isStateKnown()) return;
        int current = reference.getBrightnessValue();
        double error = targetLux - lux.getIlluminance();
        if (current > 0 && current <= 10 && error < -deadband * 2) { writeBrightness(0, now); return; }
        if (Math.abs(error) < deadband) return;
        int step = Math.max(-3, Math.min(3, (int)(error * 0.4)));
        if (step == 0) step = error > 0 ? 1 : -1;
        int next = Math.max(0, Math.min(100, current + step));
        if (current == 0 && next > 0 && next < 10) next = 10;
        if (next != current) writeBrightness(next, now);
    }
    private void writeBrightness(int value, Instant now) throws Exception {
        // A lost application-level feedback must not suppress a needed correction forever.
        if (lastBrightness == value && lastCommand != null && Duration.between(lastCommand, now).getSeconds() < 5) return;
        adapter.bus().write(brightnessGroup, value, ProcessCommunication.SCALING);
        lastBrightness = value; lastCommand = now;
    }
    private void reset() { lastOccupied = null; lastBrightness = -1; lastCommand = null; standby = false; offSent = false; }
}
