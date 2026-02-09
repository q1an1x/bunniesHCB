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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NightLightingPolicy implements LightingPolicy, Consumer<EntityEvent> {

    private static final long VACANCY_TIMEOUT_SECONDS = 300;
    private static final long CHECK_INTERVAL_MS = 5000;

    private static final int HOUR_MORNING_SWEEP = 5;
    private static final int HOUR_AUTO_OFF = 9;

    private final String name;
    private final KNXAdapter adapter;
    private final EventBus eventBus;
    private final Toggle enabledToggle;
    private final OccupancySensor occSensor;

    private final GroupAddress sceneGroup;
    private final int nightSceneNumber;
    private final GroupAddress allLightsGroup;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile Instant lastMotionTime = Instant.now();
    private boolean sceneTriggered = false;
    private boolean roomWasDarkened = false;

    private boolean morningLockout = false;

    public NightLightingPolicy(String name, KNXAdapter adapter,
                               Toggle enabledToggle, OccupancySensor occSensor, GroupAddress sceneGroup,
                               int nightSceneNumber, GroupAddress allLightsGroup) {
        this.name = name;
        this.adapter = adapter;
        this.eventBus = adapter.getRegistry().getEventBus();
        this.enabledToggle = enabledToggle;
        this.occSensor = occSensor;
        this.sceneGroup = sceneGroup;
        this.nightSceneNumber = nightSceneNumber;
        this.allLightsGroup = allLightsGroup;
    }

    @Override
    public void start() {
        eventBus.subscribe(this);

        scheduler.scheduleWithFixedDelay(this::update, 1, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        Logger.info("[" + name + "] started.");
    }

    @Override
    public void accept(EntityEvent event) {
        if (!enabledToggle.isOn()) return;

        if (event instanceof StateChangedEvent sce) {
            String id = sce.entityId();

            if (id.equals(enabledToggle.getNamedId())) {
                boolean nightActive = (boolean) sce.value();
                if (nightActive) {
                    onNightModeActivated();
                } else {
                    onNightModeDeactivated();
                }
                return;
            }

            if (enabledToggle.isOn() && id.equals(occSensor.getNamedId())) {
                boolean occupied = (boolean) sce.value();
                if (occupied) {
                    onMotionDetected();
                }
            }
        }
    }

    private synchronized void onNightModeActivated() {
        try {
            Logger.info("[" + name + "] night mode ON. sweeping room.");
            switchAllLights(false);
            morningLockout = false;
            roomWasDarkened = true;
            sceneTriggered = false;

            if (occSensor.getState()) {
                onMotionDetected();
            }
        } catch (Exception e) {
            Logger.error("[" + name + "] sweep error: " + e.getMessage());
        }
    }

    private void onNightModeDeactivated() {
        sceneTriggered = false;
        roomWasDarkened = false;
    }

    private synchronized void onMotionDetected() {
        if (morningLockout) {
            return;
        }

        lastMotionTime = Instant.now();
        roomWasDarkened = false;

        triggerNightScene();
    }

    @Override
    public synchronized void update() {
        if (!enabledToggle.isOn()) return;

        LocalTime now = LocalTime.now();
        int currentHour = now.getHour();

        try {
            if (currentHour == HOUR_MORNING_SWEEP && !morningLockout) {
                Logger.info("[" + name + "] morning sweep. turning OFF all lights and locking scene.");
                switchAllLights(false);
                roomWasDarkened = true;
                sceneTriggered = false;
                morningLockout = true;
            }

            if (morningLockout) {
                return;
            }

            if (currentHour == HOUR_AUTO_OFF) {
                Logger.info("[" + name + "] auto-off.");
                enabledToggle.setSwitchState(false);
                return;
            }

            long secondsSinceMotion = Duration.between(lastMotionTime, Instant.now()).getSeconds();

            if (!occSensor.getState()
                    && secondsSinceMotion > VACANCY_TIMEOUT_SECONDS
                    && !roomWasDarkened) {
                Logger.info("[" + name + "] room vacant. turning OFF all lights.");
                switchAllLights(false);
                roomWasDarkened = true;
                sceneTriggered = false;
            }
        } catch (Exception e) {
            Logger.error("[" + name + "] night loop error: " + e.getMessage());
        }
    }

    private void triggerNightScene() {
        try {
            Logger.info("[" + name + "] triggering night scene.");
            adapter.communicator().write(sceneGroup, nightSceneNumber, ProcessCommunication.UNSCALED);
            sceneTriggered = true;
        } catch (Exception e) {
            Logger.error("[" + name + "] scene error: " + e.getMessage());
        }
    }

    private void switchAllLights(boolean state) throws Exception {
        adapter.communicator().write(allLightsGroup, state);
    }
}