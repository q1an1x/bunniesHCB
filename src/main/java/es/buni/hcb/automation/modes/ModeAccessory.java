package es.buni.hcb.automation.modes;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.core.Entity;
import io.github.hapjava.accessories.SwitchAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Mutually exclusive virtual mode switches; OFF on the active mode returns to HOME. */
public final class ModeAccessory extends Entity implements SwitchAccessory {
    private final KNXAdapter adapter;
    private final HouseModeController controller;
    private final HouseMode mode;
    private volatile HomekitCharacteristicChangeCallback callback;

    public ModeAccessory(KNXAdapter adapter, HouseModeController controller, HouseMode mode) {
        super(adapter, "house", "mode." + mode.name().toLowerCase(Locale.ROOT));
        this.adapter = adapter; this.controller = controller; this.mode = mode;
        controller.onChange(() -> { if (callback != null) callback.changed(); });
    }
    @Override public CompletableFuture<String> getName() { return CompletableFuture.completedFuture(mode.label()); }
    @Override public CompletableFuture<Boolean> getSwitchState() {
        var current = controller.status();
        if (!adapter.isAutomationReady()) return CompletableFuture.failedFuture(new IllegalStateException("KNX is not ready"));
        // Keep a usable recovery control when the other mode switches are unavailable.
        if (mode == HouseMode.HOME && (current.phase() == HouseModeController.Phase.PAUSED || current.phase() == HouseModeController.Phase.FAILED))
            return CompletableFuture.completedFuture(false);
        if (current.phase() == HouseModeController.Phase.PAUSED || current.phase() == HouseModeController.Phase.FAILED)
            return CompletableFuture.failedFuture(new IllegalStateException(current.detail()));
        return CompletableFuture.completedFuture(current.mode() == mode);
    }
    @Override public CompletableFuture<Void> setSwitchState(boolean on) {
        if (!on && (mode == HouseMode.HOME || controller.status().mode() != mode)) return CompletableFuture.completedFuture(null);
        var completion = controller.select(on ? mode : HouseMode.HOME);
        if (completion.isCompletedExceptionally()) return completion.thenApply(ignored -> null);
        // A house-wide transition is rate-limited and can outlast a HomeKit request.
        // Acknowledge accepted intent; callbacks expose APPLYING/ACTIVE/FAILED state.
        return CompletableFuture.completedFuture(null);
    }
    @Override public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) { this.callback = callback; }
    @Override public void unsubscribeSwitchState() { callback = null; }
}
