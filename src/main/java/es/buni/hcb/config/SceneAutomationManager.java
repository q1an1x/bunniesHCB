package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.SceneController;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.config.knx.AutomationType;
import es.buni.hcb.config.knx.ScenesEnum;
import es.buni.hcb.core.events.EntityEvent;
import es.buni.hcb.core.events.EventBus;
import es.buni.hcb.core.events.SceneRecalledEvent;
import es.buni.hcb.utils.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SceneAutomationManager implements Consumer<EntityEvent> {

    private static final long SUSPENSION_TIME_HOURS = 8; // How long to disable automation for

    private final KNXAdapter adapter;
    private final EventBus eventBus;

    // Tracks active timers so we can cancel them if a scene is changed again
    private final Map<String, ScheduledFuture<?>> suspensionTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SceneAutomationManager(KNXAdapter adapter) {
        this.adapter = adapter;
        this.eventBus = adapter.getRegistry().getEventBus();
    }

    public void start() {
        eventBus.subscribe(this);
        Logger.info("[Manager] Scene Automation Supervisor Online.");
    }

    @Override
    public void accept(EntityEvent event) {
        if (event instanceof SceneRecalledEvent) {
            handleSceneRecall(((SceneRecalledEvent) event).sceneId());
        }
    }

    private void handleSceneRecall(int sceneNumber) {
        ScenesEnum scene = ScenesEnum.fromNumber(sceneNumber);

        if (scene == null) {
            // Unknown scene, ignore
            return;
        }

        if (scene.getDisabledAutomations().isEmpty()) {
            // This scene allows full automation (e.g. "Auto Mode").
            // Optionally: You could explicitly FORCE toggles ON here.
            return;
        }

        Logger.info(String.format("[Manager] Scene '%s' recalled in %s. Suspending: %s",
                scene.name(), scene.getLocation(), scene.getDisabledAutomations()));

        for (AutomationType type : scene.getDisabledAutomations()) {
            suspendAutomation(scene.getLocation(), type);
        }
    }

    private void suspendAutomation(String location, AutomationType type) {
        // Construct the Registry Key: e.g., "livingroom.toggle.constantlighting"
        String registryKey = location + ".toggle." + type.getKeySuffix();

        // 1. Fetch the Toggle
        // Assuming your adapter.getRegistry() returns a generic map-like object or has a lookup
        Object entity = adapter.getRegistry().get(registryKey);

        if (entity instanceof Toggle toggle) {

            // 2. Disable the Automation
            if (toggle.isOn()) {
                try {
                    toggle.setSwitchState(false); // Turn off the policy
                    Logger.info("[Manager] Disabled " + registryKey);
                } catch (Exception e) {
                    Logger.error("[Manager] Error disabling " + registryKey, e);
                }
            }

            // 3. Schedule Re-enable (Auto-Recovery)
            // If there's already a timer running for this specific toggle, cancel it first (extend time)
            ScheduledFuture<?> existingTask = suspensionTimers.get(registryKey);
            if (existingTask != null && !existingTask.isDone()) {
                existingTask.cancel(false);
            }

            // Schedule the wake-up call
            ScheduledFuture<?> task = scheduler.schedule(() -> {
                try {
                    Logger.info("[Manager] Timeout expired. Re-enabling " + registryKey);
                    toggle.setSwitchState(true);
                    suspensionTimers.remove(registryKey);
                } catch (Exception e) {
                    Logger.error("[Manager] Exception while scheduling " + registryKey, e);
                }
            }, SUSPENSION_TIME_HOURS, TimeUnit.HOURS);

            suspensionTimers.put(registryKey, task);
        } else {
            Logger.warn("[Manager] Could not find toggle in registry for key: " + registryKey);
        }
    }
}