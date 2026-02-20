package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Dimmable;
import es.buni.hcb.adapters.knx.entities.sensor.IlluminanceSensor;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.StateChangedEvent;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class ConstantLightingPolicy implements LightingPolicy, Consumer<EntityEvent> {

    private static final long LOOP_PERIOD_MS = 1000;
    private static final long HOLD_TIME_SECONDS = 300;
    private static final long STANDBY_TIME_SECONDS = 60;
    private static final int STANDBY_DIM_LEVEL = 20;
    private static final double KP = 0.4;
    private static final int MAX_STEP = 3;
    private static final int MIN_DIM_THRESHOLD = 10;

    private final String name;
    private final KNXAdapter adapter;
    private final EventBus eventBus;
    private final Toggle enabledToggle;
    private final Toggle nightModeToggle;

    private final IlluminanceSensor luxSensor;
    private final OccupancySensor occSensor;

    private final GroupAddress controlGroup;
    private final GroupAddress allLightsGroup;
    private final Dimmable reference;

    private final double targetLux;
    private final double deadband;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile Instant lastMotionTime = Instant.now();
    private final AtomicInteger lastSentBrightness = new AtomicInteger(-1);
    private boolean isStandbyActive = false;

    public ConstantLightingPolicy(String name, KNXAdapter adapter, Toggle enabledToggle, Toggle nightModeToggle,
                                  IlluminanceSensor luxSensor, OccupancySensor occSensor,
                                  GroupAddress controlGroupAddress, GroupAddress allLightsGroup,
                                  Dimmable referenceLight, double targetLux, double deadband) {
        this.name = name;
        this.adapter = adapter;
        this.eventBus = adapter.getRegistry().getEventBus();
        this.enabledToggle = enabledToggle;
        this.nightModeToggle = nightModeToggle;
        this.luxSensor = luxSensor;
        this.occSensor = occSensor;
        this.controlGroup = controlGroupAddress;
        this.allLightsGroup = allLightsGroup;
        this.reference = referenceLight;
        this.targetLux = targetLux;
        this.deadband = deadband;
    }

    @Override
    public void start() {
        enabledToggle.setOnToggleListener(this::update);
        eventBus.subscribe(this);

        scheduler.scheduleWithFixedDelay(this::update, 1000, LOOP_PERIOD_MS, TimeUnit.MILLISECONDS);
        Logger.info("[" + name + "] started. target: " + targetLux + " lux.");
    }

    @Override
    public void accept(EntityEvent event) {
        if (event instanceof StateChangedEvent sce
                && sce.entityId().equals(occSensor.getNamedId())) {
            if (sce.value() instanceof Boolean occupied && occupied) {
                lastMotionTime = Instant.now();
                if (isStandbyActive) {
                    isStandbyActive = false;
                    update();
                }
            }
        }
    }

    @Override
    public synchronized void update() {
        if (!enabledToggle.isOn()) {
            resetState();
            return;
        }

        try {
            handleOccupancyAndLighting();
        } catch (Exception e) {
            Logger.error("[" + name + "] control loop error: " + e.getMessage());
        }
    }

    private void handleOccupancyAndLighting() throws Exception {
        if (nightModeToggle.isOn()) {
            lastSentBrightness.set(-1);
            return;
        }

        if (occSensor.getState()) {
            lastMotionTime = Instant.now();
            isStandbyActive = false;
        }

        long secondsSinceMotion = Duration.between(lastMotionTime, Instant.now()).getSeconds();

        if (secondsSinceMotion > (HOLD_TIME_SECONDS + STANDBY_TIME_SECONDS)) {
            if (reference.getBrightnessValue() > 0 || lastSentBrightness.get() != 0) {
                Logger.info("[" + name + "] room vacant. turning OFF all lights.");
                switchAllLights(false);
                lastSentBrightness.set(0);
            }
            return;

        } else if (secondsSinceMotion > HOLD_TIME_SECONDS) {
            if (!isStandbyActive) {
                Logger.info("[" + name + "] entering standby (" + STANDBY_DIM_LEVEL + "%).");
                writeBrightness(STANDBY_DIM_LEVEL);
                isStandbyActive = true;
            }
            return;
        }

        calculateAndApplyRegulation();
    }

    private void calculateAndApplyRegulation() throws Exception {
        double currentLux = luxSensor.getIlluminance();
        int currentDim = reference.getBrightnessValue();
        double error = targetLux - currentLux;

        if (currentDim <= MIN_DIM_THRESHOLD && currentDim > 0 && error < -(deadband * 2)) {
            Logger.info("[" + name + "] daylight sufficient. cutting power.");
            writeBrightness(0);
            return;
        }

        if (Math.abs(error) < deadband) return;

        int calculatedStep = (int) (error * KP);
        calculatedStep = Math.max(-MAX_STEP, Math.min(MAX_STEP, calculatedStep));

        if (calculatedStep == 0) {
            calculatedStep = (error > 0) ? 1 : -1;
        }

        int newBrightness = Math.max(0, Math.min(100, currentDim + calculatedStep));

        if (newBrightness != currentDim) {
            if (currentDim == 0 && newBrightness > 0 && newBrightness < MIN_DIM_THRESHOLD) {
                newBrightness = MIN_DIM_THRESHOLD;
            }
            writeBrightness(newBrightness);
        }
    }

    private void writeBrightness(int percent) throws Exception {
        if (lastSentBrightness.get() == percent) return;
        adapter.communicator().write(controlGroup, percent, ProcessCommunication.SCALING);
        lastSentBrightness.set(percent);
    }

    private void switchAllLights(boolean state) throws Exception {
        adapter.communicator().write(allLightsGroup, state);
    }

    private void resetState() {
        lastSentBrightness.set(-1);
        isStandbyActive = false;
    }
}