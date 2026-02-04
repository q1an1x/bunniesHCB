package es.buni.hcb.adapters.broadlink.oven;

import es.buni.hcb.adapters.broadlink.BroadlinkAdapter;
import es.buni.hcb.adapters.broadlink.oven.homekit.*;
import es.buni.hcb.core.Entity;
import es.buni.hcb.utils.Logger;

import io.github.hapjava.services.Service;
import io.github.hapjava.services.impl.HeaterCoolerService;
import io.github.hapjava.services.impl.ValveService;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public class BSHOven extends Entity {
    private static final int DEFAULT_PORT = 80;

    // Siemens CS289ABS6W
    private static final int SIEMENS_OVEN_DEV_TYPE = 0x6579;

    private final BroadlinkAdapter adapter;
    private final String host;
    private final int port;
    private final byte[] mac;
    private final int devType;

    private static final long POLL_INTERVAL_SECONDS = 5;
    private static final long MAX_BACKOFF_SECONDS = 3600;
    private static final long PRE_SYNC_OFFSET_MS = 2_000;
    private static final long SYNC_INTERVAL_MS = TimeUnit.HOURS.toMillis(8);
    private final AtomicBoolean syncPending = new AtomicBoolean(false);

    private int consecutivePollFailures = 0;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private volatile OvenState state;

    private final OvenHomeKitTimer homeKitTimer = new OvenHomeKitTimer(this);
    private final OvenHomeKitDoor homeKitDoor = new OvenHomeKitDoor(this);
    private final OvenHomeKitMenu homeKitMenu = new OvenHomeKitMenu(this);
    private final OvenHomeKitTemperature homeKitTemperature = new OvenHomeKitTemperature(this);

    private OvenCommandBuilder uncommittedCommandBuilder;

    public void setTemperature(int temperature) {
        OvenCommandBuilder builder = getUncommittedCommandBuilder();

        if (!builder.isModeSet()) {
            builder.setMode(OvenMode.CONVECTION);
            homeKitMenu.getOvenModeCallback().changed();
        }

        int clampedTemp;
        if (builder.getMode().getPresetType() == OvenPresetType.STEAM_GRILL) {
            clampedTemp = clampTemperature(temperature, builder.getMode());
            builder.setSteamTemperature(clampedTemp);
        } else {
            clampedTemp = clampTemperature(temperature, builder.getMode());
            builder.setTemperature(clampedTemp);
        }

        if (homeKitTemperature.getTemperatureCallback() != null) {
            // Defer HomeKit callback to next tick so the clamped temperature
            // is visible when HomeKit reads the characteristic.
            scheduler.schedule(
                    () -> homeKitTemperature.getTemperatureCallback().changed(),
                    0,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private int clampTemperature(int temperature, OvenMode mode) {
        if (temperature > mode.getMaxTemperature()) {
            return mode.getMaxTemperature();
        } else if (temperature < mode.getMinTemperature()) {
            return mode.getMinTemperature();
        } else {
            return temperature;
        }
    }

    public int getTemperature() {
        if (uncommittedCommandBuilder != null) {
            if (uncommittedCommandBuilder.isModeSet()) {
                if (uncommittedCommandBuilder.getMode().getPresetType() == OvenPresetType.STEAM_GRILL
                        && uncommittedCommandBuilder.isSteamTemperatureSet()) {
                    return uncommittedCommandBuilder.getSteamTemperature();
                } else if (uncommittedCommandBuilder.isTemperatureSet()) {
                    return uncommittedCommandBuilder.getTemperature();
                } else {
                    return uncommittedCommandBuilder.getMode().getDefaultTemperature();
                }
            }
        }

        if (state != null && state.getMode() != OvenMode.NONE) {
            if (state.getMode().getPresetType() == OvenPresetType.STEAM_GRILL) {
                return state.getSteamTemperature();
            } else {
                return state.getTemperature();
            }
        }

        return OvenMode.CONVECTION.getDefaultTemperature();
    }

    public void setDuration(int seconds) {
        getUncommittedCommandBuilder()
                .setDuration(seconds);
    }

    public void setMode(OvenMode mode) {
        getUncommittedCommandBuilder()
                .setMode(mode);

        if (mode.getPresetType() ==  OvenPresetType.STEAM_GRILL) {
            getUncommittedCommandBuilder()
                    .setSteamTemperature(mode.getDefaultTemperature());
        } else {
            getUncommittedCommandBuilder()
                    .setTemperature(mode.getDefaultTemperature());
        }
        homeKitTemperature.getTemperatureCallback().changed();
    }

    public OvenMode getMode() {
        if (uncommittedCommandBuilder != null) {
            if (uncommittedCommandBuilder.getMode() != null) {
                return uncommittedCommandBuilder.getMode();
            }
        }

        if (state != null) {
            return state.getMode();
        }

        return OvenMode.NONE;
    }

    public void on() throws IOException {
        sendCommand(
                OvenCommandBuilder
                        .create()
                        .setRunState(OvenCommandRunState.READY)
        );
    }

    public void off() throws IOException {
        sendCommand(
                OvenCommandBuilder
                        .create()
                        .setRunState(OvenCommandRunState.STANDBY)
        );
    }

    private OvenCommandBuilder getUncommittedCommandBuilder() {
        if  (uncommittedCommandBuilder == null) {
            uncommittedCommandBuilder = new OvenCommandBuilder();
        }

        return uncommittedCommandBuilder;
    }

    public void activate() throws IOException {
        pollState();
        if (state.isStandingBy()) {
            sendCommand(OvenCommandBuilder.create().setRunState(OvenCommandRunState.READY));
        }

        if (state.getRunState() == OvenRunState.SCHEDULED) {
            getUncommittedCommandBuilder()
                    .setRunState(OvenCommandRunState.SCHEDULED_RUN);
        } else if (state.getRunState() == OvenRunState.RUNNING) {
            sendCommand(OvenCommandBuilder.create());

            run(getUncommittedCommandBuilder());
        } else {
            run(getUncommittedCommandBuilder());
        }

        uncommittedCommandBuilder = null;
        pollState();
    }

    public void deactivate() throws IOException, InterruptedException {
        on();

        uncommittedCommandBuilder = null;
        pollState();
    }

    public boolean isActive() {
        if (state == null) {
            return false;
        }

        return state.getRunState() == OvenRunState.RUNNING
                || state.getRunState() == OvenRunState.SCHEDULED
                || state.getRunState() == OvenRunState.PAUSED;
    }

    public boolean isActive(boolean poll) throws IOException {
        if (poll) {
            pollState();
        }

        return isActive();
    }

    public void run(OvenCommandBuilder builder) throws IOException {
        if (! builder.isDurationSet()) {
            builder.setDuration(1200);
        }

        sendCommand(
                builder.setRunState(OvenCommandRunState.TIMED_RUN)
        );
    }

    public BSHOven(
            BroadlinkAdapter adapter,
            String location,
            String id,
            String host,
            String mac
    ) {
        String[] parts = mac.split(":");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }

        this(adapter, location, id, host, DEFAULT_PORT, bytes);
    }

    public BSHOven(
            BroadlinkAdapter adapter,
            String location,
            String id,
            String host,
            int port,
            byte[] mac
    ) {
        super(adapter, location, id);
        this.adapter = adapter;
        this.host = host;
        this.port = port;
        this.mac = mac;
        this.devType = SIEMENS_OVEN_DEV_TYPE;
    }

    @Override
    public void initialize() {
        try {
            Logger.info("Initializing Oven: " + host);
            adapter.authenticate(host, port, mac, SIEMENS_OVEN_DEV_TYPE);
            pollState();

            scheduler.schedule(this::safePoll, 1, TimeUnit.SECONDS);
            scheduleNextSyncStep(true);

        } catch (IOException e) {
            Logger.error("Failed to init oven", e);
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void triggerImmediateRepairSync() {
        if (syncPending.compareAndSet(false, true)) {
            scheduleNextSyncStep(true);
        }
    }

    private void scheduleNextSyncStep() {
        scheduleNextSyncStep(false);
    }

    private void scheduleNextSyncStep(boolean immediate) {
        final long now = System.currentTimeMillis();

        long baseTime = immediate ? now : (now + SYNC_INTERVAL_MS);
        long remainderToMinute = 60000 - (baseTime % 60000);
        final long executionTarget = baseTime + remainderToMinute;

        final long delayUntilPoll = (executionTarget - PRE_SYNC_OFFSET_MS) - now;
        scheduler.schedule(
                () -> syncClock(executionTarget, immediate),
                Math.max(delayUntilPoll, 0),
                TimeUnit.MILLISECONDS
        );
    }

    private void safePoll() {
        long nextDelay = POLL_INTERVAL_SECONDS;

        try {
            pollState();
            consecutivePollFailures = 0;
        }
        catch (Exception e) {
            consecutivePollFailures++;

            if (consecutivePollFailures >= 3) {
                int exponent = consecutivePollFailures - 3;
                nextDelay = Math.min(
                        POLL_INTERVAL_SECONDS * (1L << exponent),
                        MAX_BACKOFF_SECONDS
                );

                Logger.warn("Oven: poll failed (" + consecutivePollFailures +
                        " consecutive). Backing off to " + nextDelay + "s"
                );
            } else {
                if (consecutivePollFailures > 1) {
                    Logger.warn("Oven: poll failed: " + e.getMessage());
                }
            }
        }

        scheduler.schedule(this::safePoll, nextDelay, TimeUnit.SECONDS);
    }

    public synchronized void pollState() throws IOException {
        OvenState oldState = state;
        boolean wasActive = isActive();
        state = OvenState.fromJsonObject(
                adapter.getDeviceState(host, port, mac, devType)
        );

        if (oldState == null) {
            return;
        }

        if (oldState.getDoorState() != state.getDoorState()) {
            if (homeKitDoor.getDoorStateCallback() != null) {
                Logger.info("Oven: door state changed to " + state.getDoorState().toString());
                homeKitDoor.getDoorStateCallback().changed();
            }
        }

        if (wasActive != isActive()) {
            if (homeKitTimer.getOvenActiveCallback() != null) {
                homeKitTimer.getOvenActiveCallback().changed();
            }
        }

        if (oldState.isStandingBy() != state.isStandingBy()) {
            homeKitMenu.getOvenStateCallback().changed();
            homeKitTemperature.getOvenStateCallback().changed();
        }

        if (oldState.isRunning() != state.isRunning()) {
            if (homeKitTimer.getOvenRunningCallback() != null) {
                homeKitTimer.getOvenRunningCallback().changed();
            }
        }

        if (oldState.getDuration() != state.getDuration()) {
            if (homeKitTimer.getOvenDurationCallback() != null) {
                homeKitTimer.getOvenDurationCallback().changed();
            }
        }

        if (oldState.getDurationRemaining() != state.getDurationRemaining()) {
            if (homeKitTimer.getOvenDurationRemainingCallback() != null) {
                homeKitTimer.getOvenDurationRemainingCallback().changed();
            }
        }

        if (oldState.getMode() != state.getMode()) {
            if (homeKitMenu.getOvenModeCallback() != null) {
                homeKitMenu.getOvenModeCallback().changed();
            }
        }
    }

    private void sendCommand(OvenCommandBuilder builder) throws IOException {
        rawSend(builder);

        triggerImmediateRepairSync();
    }

    private void rawSend(OvenCommandBuilder builder) throws IOException {
        adapter.setDeviceState(host, port, mac, devType, builder.build());
    }

    private void syncClock(long targetExecutionTime, final boolean isRepairSync) {
        try {
            if (isRepairSync) {
                syncPending.set(false);
            } else {
                scheduleNextSyncStep();
            }

            if (isActive(true)) {
                if (isRepairSync) syncPending.set(false);
                Logger.info("Oven: skipping clock sync because the oven is active");
                return;
            }

            long now = System.currentTimeMillis();
            long delayToTarget = targetExecutionTime - now;

            if (delayToTarget <= 0) {
                if (isRepairSync) syncPending.set(false);
                Logger.warn("Oven: Polling too slow. Repair aborted.");
                return;
            }

            scheduler.schedule(() -> {
                try {
                    rawSend(OvenCommandBuilder.create());

                    if (isRepairSync) syncPending.set(false);
                    Logger.info("Oven: Clock synced at " + new java.util.Date());
                } catch (Exception e) {
                    if (isRepairSync) syncPending.set(false);
                    Logger.error("Failed to sync clock", e);
                }
            }, delayToTarget, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            if (isRepairSync) syncPending.set(false);
            Logger.error("Failed during clock sync preparation", e);
        }
    }

    public OvenState getState() {
        return state;
    }

    @Override
    public Collection<Service> getServices() {
        ValveService valveService = new ValveService(homeKitTimer);
        valveService.addLinkedService(new HeaterCoolerService(homeKitTemperature));

        return Stream.<Collection<Service>>of(
                super.getServices(),
                homeKitMenu.getServices(),
                homeKitDoor.getServices(),
                Collections.singleton(valveService)
        ).flatMap(Collection::stream).toList();
    }
}