package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.StateChangedEvent;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;

import java.time.Instant;
import java.time.LocalTime;
import java.util.function.Consumer;

public class AutoLightingPolicy implements LightingPolicy, Consumer<EntityEvent> {
    private final String name;
    private final KNXAdapter adapter;
    private final EventBus eventBus;
    private final Toggle enabledToggle;
    private final OccupancySensor occSensor;

    private final GroupAddress sceneGroup;
    private final int sceneNumber;
    private final GroupAddress allLightsGroup;

    public AutoLightingPolicy(String name, KNXAdapter adapter, int sceneNumber,
                              Toggle enabledToggle, OccupancySensor occSensor,
                              GroupAddress sceneGroup, GroupAddress allLightsGroup) {
        this.name = name;
        this.adapter = adapter;
        this.sceneNumber = sceneNumber;
        this.eventBus = adapter.getRegistry().getEventBus();
        this.enabledToggle = enabledToggle;
        this.occSensor = occSensor;
        this.sceneGroup = sceneGroup;
        this.allLightsGroup = allLightsGroup;
    }

    @Override
    public void start() {
        eventBus.subscribe(this);

        Logger.info("[" + name + "] started.");
    }

    @Override
    public void accept(EntityEvent event) {
        if (!enabledToggle.isOn()) return;

        if (event instanceof StateChangedEvent sce) {
            String id = sce.entityId();

            if (enabledToggle.isOn() && id.equals(occSensor.getNamedId())) {
                boolean occupied = (boolean) sce.value();
                if (occupied) {
                    onMotionDetected();
                } else {
                    try {
                        switchAllLights(false);
                    } catch (Exception e) {
                        Logger.error("[" + name + "] failed to switch all lights.", e);
                    }
                }
            }
        }
    }

    private synchronized void onMotionDetected() {
        triggerScene();
    }

    @Override
    public synchronized void update() {}

    private void triggerScene() {
        try {
            Logger.info("[" + name + "] triggering scene.");
            adapter.communicator().write(sceneGroup, sceneNumber, ProcessCommunication.UNSCALED);
        } catch (Exception e) {
            Logger.error("[" + name + "] scene error: " + e.getMessage());
        }
    }

    private void switchAllLights(boolean state) throws Exception {
        adapter.communicator().write(allLightsGroup, state);
    }
}