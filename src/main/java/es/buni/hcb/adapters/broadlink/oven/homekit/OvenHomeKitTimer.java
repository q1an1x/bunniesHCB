package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.adapters.broadlink.oven.OvenRunState;
import io.github.hapjava.accessories.ValveAccessory;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithDuration;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithRemainingDuration;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.common.ActiveEnum;
import io.github.hapjava.characteristics.impl.common.InUseEnum;
import io.github.hapjava.characteristics.impl.valve.ValveTypeEnum;

import java.util.concurrent.CompletableFuture;

public class OvenHomeKitTimer extends OvenHomeKitComponent implements
        ValveAccessory, AccessoryWithDuration, AccessoryWithRemainingDuration {
    protected HomekitCharacteristicChangeCallback ovenActiveCallback;
    protected HomekitCharacteristicChangeCallback ovenRunningCallback;
    protected HomekitCharacteristicChangeCallback ovenDurationRemainingCallback;
    protected HomekitCharacteristicChangeCallback ovenDurationCallback;

    public HomekitCharacteristicChangeCallback getOvenActiveCallback() {
        return ovenActiveCallback;
    }

    public HomekitCharacteristicChangeCallback getOvenRunningCallback() {
        return ovenRunningCallback;
    }

    public HomekitCharacteristicChangeCallback getOvenDurationRemainingCallback() {
        return ovenDurationRemainingCallback;
    }

    public HomekitCharacteristicChangeCallback getOvenDurationCallback() {
        return ovenDurationCallback;
    }

    public OvenHomeKitTimer(BSHOven oven) {
        super(oven, "timer");
    }

    @Override
    public CompletableFuture<ActiveEnum> getValveActive() {
        return CompletableFuture.completedFuture(
                oven.isActive()
                        ? ActiveEnum.ACTIVE
                        : ActiveEnum.INACTIVE
        );
    }

    @Override
    public CompletableFuture<Void> setValveActive(ActiveEnum active) throws Exception {
        if (active == ActiveEnum.ACTIVE) {
            oven.activate();
        } else {
            oven.deactivate();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeValveActive(HomekitCharacteristicChangeCallback callback) {
        ovenActiveCallback = callback;
    }

    @Override
    public void unsubscribeValveActive() {
        ovenActiveCallback = null;
    }

    @Override
    public CompletableFuture<InUseEnum> getValveInUse() {
        return CompletableFuture.completedFuture(
                oven.getState().getRunState() == OvenRunState.RUNNING
                        ? InUseEnum.IN_USE
                        : InUseEnum.NOT_IN_USE
        );
    }

    @Override
    public void subscribeValveInUse(HomekitCharacteristicChangeCallback callback) {
        ovenRunningCallback = callback;
    }

    @Override
    public void unsubscribeValveInUse() {
        ovenRunningCallback = null;
    }

    @Override
    public CompletableFuture<ValveTypeEnum> getValveType() {
        return CompletableFuture.completedFuture(ValveTypeEnum.IRRIGATION);
    }

    @Override
    public void subscribeValveType(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeValveType() {
    }

    @Override
    public CompletableFuture<Integer> getRemainingDuration() {
        return CompletableFuture.completedFuture(
                oven.getState().getDurationRemaining()
        );
    }

    @Override
    public void subscribeRemainingDuration(HomekitCharacteristicChangeCallback callback) {
        ovenDurationRemainingCallback = callback;
    }

    @Override
    public void unsubscribeRemainingDuration() {
        ovenDurationRemainingCallback = null;
    }

    @Override
    public CompletableFuture<Integer> getSetDuration() {
        return CompletableFuture.completedFuture(
                oven.getState().getDuration()
        );
    }

    @Override
    public CompletableFuture<Void> setSetDuration(int value) {
        oven.setDuration(value);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeSetDuration(HomekitCharacteristicChangeCallback callback) {
        ovenDurationCallback = callback;
    }

    @Override
    public void unsubscribeSetDuration() {
        ovenDurationCallback = null;
    }
}
