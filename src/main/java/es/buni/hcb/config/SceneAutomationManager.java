package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
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

    private static final long SUSPENSION_TIME_HOURS = 8;

    private final KNXAdapter adapter;
    private final EventBus eventBus;

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

        if (scene == null || scene.getDisabledAutomations().isEmpty()) {
            return;
        }

        Logger.info(String.format("[Manager] Scene '%s' recalled in %s. Suspending: %s",
                scene.name(), scene.getLocation(), scene.getDisabledAutomations()));

        for (AutomationType type : scene.getDisabledAutomations()) {
            suspendAutomation(scene.getLocation(), type);
        }
    }

    private void suspendAutomation(String location, AutomationType type) {
        String registryKey = location + ".toggle." + type.getKeySuffix();

        Object entity = adapter.getRegistry().get(registryKey);
        if (entity instanceof Toggle toggle) {
            if (! toggle.isOn()) {
                return;
            }

            try {
                toggle.setSwitchState(false);
                Logger.info("[Manager] Disabled " + registryKey);
            } catch (Exception e) {
                Logger.error("[Manager] Error disabling " + registryKey, e);
            }

            if (type == AutomationType.NIGHT) {
                return;
            }

            ScheduledFuture<?> existingTask = suspensionTimers.get(registryKey);
            if (existingTask != null && !existingTask.isDone()) {
                existingTask.cancel(false);
            }

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