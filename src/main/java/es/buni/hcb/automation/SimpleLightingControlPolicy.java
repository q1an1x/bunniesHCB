package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.lighting.Light;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.*;

public final class SimpleLightingControlPolicy extends ManagedLightingPolicy {
    private final OccupancySensor occupancy;
    private final Light mainLight, sinkLight;
    private boolean owned;
    private long generation = -1;

    public SimpleLightingControlPolicy(String name, KNXAdapter adapter, OccupancySensor occupancy,
                                       Light mainLight, Light sinkLight) {
        super(name, adapter, occupancy.getLocation(), PolicyKind.PRESENCE);
        this.occupancy = occupancy; this.mainLight = mainLight; this.sinkLight = sinkLight;
    }
    @Override protected void evaluate() { }
    @Override protected void handle(EntityEvent event) throws Exception {
        if (generation != adapter.generation()) { owned = false; generation = adapter.generation(); }
        if (!mainLight.hasSwitchState() || !sinkLight.hasSwitchState() || mainLight.isOn()) return;
        if (event instanceof StateChangedEvent state && state.entityId().equals(occupancy.getNamedId())
                && state.value() instanceof Boolean occupied) {
            if (occupied && !sinkLight.isOn()) { sinkLight.on(); owned = true; }
            else if (!occupied && owned) { sinkLight.off(); owned = false; }
        }
    }
}
