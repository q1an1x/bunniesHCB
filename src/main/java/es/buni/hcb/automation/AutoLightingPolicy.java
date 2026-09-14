package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.*;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;

public final class AutoLightingPolicy extends ManagedLightingPolicy {
    private final Toggle enabled, night;
    private final OccupancySensor occupancy;
    private final GroupAddress sceneGroup, offGroup;
    private final int scene;

    public AutoLightingPolicy(String name, KNXAdapter adapter, int scene, Toggle enabled, Toggle night,
                              OccupancySensor occupancy, GroupAddress sceneGroup, GroupAddress offGroup) {
        super(name, adapter);
        if (scene < 0 || scene > 63) throw new IllegalArgumentException("Scene must be 0..63");
        this.scene = scene; this.enabled = enabled; this.night = night; this.occupancy = occupancy;
        this.sceneGroup = sceneGroup; this.offGroup = offGroup;
        adapter.declareCommand(name, "scene", sceneGroup, "18.001");
        adapter.declareCommand(name, "off", offGroup, "1.001");
    }
    @Override protected void evaluate() { }
    @Override protected void handle(EntityEvent event) throws Exception {
        if (!enabled.isOn() || !night.isStateKnown() || night.isOn() || !occupancy.isStateKnown()) return;
        if (event instanceof StateChangedEvent state && state.entityId().equals(occupancy.getNamedId())
                && state.value() instanceof Boolean occupied) {
            if (occupied) adapter.bus().write(sceneGroup, scene, ProcessCommunication.UNSCALED);
            else adapter.bus().write(offGroup, false);
        }
    }
}
