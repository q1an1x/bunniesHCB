package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.adapters.broadlink.oven.OvenMode;
import io.github.hapjava.accessories.InputSourceAccessory;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithIdentifier;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.common.IsConfiguredEnum;
import io.github.hapjava.characteristics.impl.inputsource.CurrentVisibilityStateEnum;
import io.github.hapjava.characteristics.impl.inputsource.InputSourceTypeEnum;

import java.util.concurrent.CompletableFuture;

public class OvenHomeKitMenuItem extends OvenHomeKitComponent
        implements InputSourceAccessory, AccessoryWithIdentifier {
    private final int identifier;

    public OvenHomeKitMenuItem(BSHOven oven, OvenMode mode) {
        super(oven, "menu." + mode.ordinal());
        this.identifier = mode.ordinal();
    }

    @Override
    public CompletableFuture<String> getConfiguredName() {
        return CompletableFuture.completedFuture(
                getStoredConfiguredName()
        );
    }

    @Override
    public CompletableFuture<Void> setConfiguredName(String name) throws Exception {
        setStoredConfiguredName(name);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeConfiguredName(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeConfiguredName() {
    }

    @Override
    public CompletableFuture<IsConfiguredEnum> isConfigured() {
        return CompletableFuture.completedFuture(IsConfiguredEnum.CONFIGURED);
    }

    @Override
    public CompletableFuture<Void> setIsConfigured(IsConfiguredEnum state) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeIsConfigured(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeIsConfigured() {
    }

    @Override
    public CompletableFuture<InputSourceTypeEnum> getInputSourceType() {
        return CompletableFuture.completedFuture(InputSourceTypeEnum.APPLICATION);
    }

    @Override
    public void subscribeInputSourceType(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeInputSourceType() {
    }

    @Override
    public CompletableFuture<CurrentVisibilityStateEnum> getCurrentVisibilityState() {
        return CompletableFuture.completedFuture(CurrentVisibilityStateEnum.SHOWN);
    }

    @Override
    public void subscribeCurrentVisibilityState(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeCurrentVisibilityState() {
    }

    @Override
    public CompletableFuture<Integer> getIdentifier() {
        return CompletableFuture.completedFuture(identifier);
    }
}
