package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Switch;
import es.buni.hcb.utils.Logger;
import io.github.hapjava.accessories.LightbulbAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.concurrent.CompletableFuture;

public class Light extends Switch implements LightbulbAccessory {
    public static Light fromConvention(KNXAdapter adapter, String location, String id,
                                       int switchMainGroup, int switchMiddleGroup, int switchSubGroup)
            throws Exception {
        return new Light(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 1
        );
    }

    public Light(KNXAdapter adapter, String location, String id,
                    int switchMainGroup, int switchMiddleGroup, int switchSubGroup,
                    int statusSwitchMainGroup, int statusSwitchMiddleGroup, int statusSwitchSubGroup)
            throws Exception {
        super(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                statusSwitchMainGroup, statusSwitchMiddleGroup, statusSwitchSubGroup);
    }

    @Override
    protected void onSwitchStatusChanged(boolean newValue) {
        super.onSwitchStatusChanged(newValue);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    @Override
    public CompletableFuture<Boolean> getLightbulbPowerState() {
        return stateFuture(statusSwitchAddress, isOn());
    }

    @Override
    public CompletableFuture<Void> setLightbulbPowerState(boolean powerState) throws Exception {
        adapter.manualOverrides().hold(getLocation(), es.buni.hcb.automation.ManualOverrides.LIGHT_LEVEL);
        if (powerState) {
            on();
            Logger.info("HomeKit turned on " + getNamedId());
        } else {
            off();
            Logger.info("HomeKit turned off " + getNamedId());
        }

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeLightbulbPowerState(HomekitCharacteristicChangeCallback callback) {
        this.subscribeCallback = callback;
    }

    @Override
    public void unsubscribeLightbulbPowerState() {
        this.subscribeCallback = null;
    }

    @Override
    public void identify() {
        if (!hasSwitchState()) return;
        boolean originalState;
        try {
            originalState = isOn();
        } catch (Exception e) {
            Logger.error("HomeKit: Failed to read state for identification", e);
            return;
        }

        long epoch = adapter.generation(), intent = adapter.intentRevision();
        long manual = adapter.manualOverrides().revision(getLocation());
        CompletableFuture.runAsync(() -> adapter.runInSession(epoch, () -> {
            try {
                if (intent != adapter.intentRevision() || manual != adapter.manualOverrides().revision(getLocation())) return;
                toggle();

                Thread.sleep(200);
                if (intent != adapter.intentRevision() || manual != adapter.manualOverrides().revision(getLocation())) return;
                if (originalState) {
                    on();
                } else {
                    off();
                }
            } catch (Exception e) {
                Logger.error("HomeKit: Failed to restore state after identification", e);
            }
        }));
    }
}
