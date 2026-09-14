package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.lighting.Light;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.StateChangedEvent;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;

import java.util.function.Consumer;

public class SimpleLightingControlPolicy implements LightingPolicy, Consumer<EntityEvent> {
    private final String name;
    private final KNXAdapter adapter;
    private final EventBus eventBus;
    private final OccupancySensor occSensor;

    private final Light logicLight;
    private final Light light;

    public SimpleLightingControlPolicy(String name, KNXAdapter adapter,
                                       OccupancySensor occSensor,
                                       Light logicLight, Light light) {
        this.name = name;
        this.adapter = adapter;
        this.eventBus = adapter.getRegistry().getEventBus();
        this.occSensor = occSensor;
        this.logicLight = logicLight;
        this.light = light;
    }

    @Override
    public void start() {
        eventBus.subscribe(this);

        Logger.info("[" + name + "] started.");
    }

    @Override
    public void accept(EntityEvent event) {
        if (logicLight.isOn()) return;

        if (event instanceof StateChangedEvent sce) {
            String id = sce.entityId();

            if (! logicLight.isOn() && id.equals(occSensor.getNamedId())) {
                boolean occupied = (boolean) sce.value();
                if (occupied) {
                    onMotionDetected();
                } else {
                    try {
                        light.off();
                    } catch (Exception e) {
                        Logger.error("[" + name + "] failed to switch off light.", e);
                    }
                }
            }
        }
    }

    private synchronized void onMotionDetected() {
        triggerControl();
    }

    @Override
    public synchronized void update() {}

    private void triggerControl() {
        try {
            Logger.info("[" + name + "] triggering light control.");
            light.on();
        } catch (Exception e) {
            Logger.error("[" + name + "] light control error: " + e.getMessage());
        }
    }
}