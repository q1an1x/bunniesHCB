package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import io.github.hapjava.accessories.HeaterCoolerAccessory;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithHeatingThresholdTemperature;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.heatercooler.CurrentHeaterCoolerStateEnum;
import io.github.hapjava.characteristics.impl.heatercooler.TargetHeaterCoolerStateEnum;

import java.util.concurrent.CompletableFuture;

public class OvenHomeKitTemperature extends OvenHomeKitComponent implements
        HeaterCoolerAccessory, AccessoryWithHeatingThresholdTemperature {
    protected HomekitCharacteristicChangeCallback ovenStateCallback;
    protected HomekitCharacteristicChangeCallback temperatureCallback;

    public OvenHomeKitTemperature(BSHOven oven) {
        super(oven, "temperature");
    }

    @Override
    public CompletableFuture<Double> getCurrentTemperature() {
        return getHeatingThresholdTemperature();
    }

    public HomekitCharacteristicChangeCallback getOvenStateCallback() {
        return ovenStateCallback;
    }

    @Override
    public CompletableFuture<Boolean> isActive() {
        return CompletableFuture.completedFuture(
                ! oven.getState().isStandingBy()
        );
    }

    @Override
    public CompletableFuture<Void> setActive(boolean state) throws Exception {
        if (state) {
            oven.on();
        } else {
            oven.off();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<CurrentHeaterCoolerStateEnum> getCurrentHeaterCoolerState() {
        return CompletableFuture.completedFuture(
                CurrentHeaterCoolerStateEnum.HEATING
        );
    }

    @Override
    public CompletableFuture<TargetHeaterCoolerStateEnum> getTargetHeaterCoolerState() {
        return CompletableFuture.completedFuture(
                TargetHeaterCoolerStateEnum.HEAT
        );
    }

    @Override
    public CurrentHeaterCoolerStateEnum[] getCurrentHeaterCoolerStateValidValues() {
        return new CurrentHeaterCoolerStateEnum[]{
                CurrentHeaterCoolerStateEnum.HEATING,
        };
    }

    @Override
    public TargetHeaterCoolerStateEnum[] getTargetHeaterCoolerStateValidValues() {
        return new TargetHeaterCoolerStateEnum[] {
                TargetHeaterCoolerStateEnum.HEAT
        };
    }

    @Override
    public CompletableFuture<Void> setTargetHeaterCoolerState(TargetHeaterCoolerStateEnum state) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeCurrentHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeCurrentHeaterCoolerState() {
    }

    @Override
    public void subscribeTargetHeaterCoolerState(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeTargetHeaterCoolerState() {
    }

    @Override
    public void subscribeActive(HomekitCharacteristicChangeCallback callback) {
        ovenStateCallback = callback;
    }

    @Override
    public void unsubscribeActive() {
        ovenStateCallback = null;
    }

    @Override
    public void subscribeCurrentTemperature(HomekitCharacteristicChangeCallback callback) {
    }

    @Override
    public void unsubscribeCurrentTemperature() {
    }

    @Override
    public CompletableFuture<Double> getHeatingThresholdTemperature() {
        return CompletableFuture.completedFuture(
                (double) oven.getTemperature()
        );
    }

    @Override
    public double getMinHeatingThresholdTemperature() {
        return 40;
    }

    @Override
    public double getMaxHeatingThresholdTemperature() {
        return 230;
    }

    @Override
    public double getStepHeatingThresholdTemperature() {
        return 5.0;
    }

    @Override
    public void setHeatingThresholdTemperature(Double value) throws Exception {
        oven.setTemperature(value.intValue());
    }

    @Override
    public void subscribeHeatingThresholdTemperature(HomekitCharacteristicChangeCallback callback) {
        temperatureCallback = callback;
    }

    @Override
    public void unsubscribeHeatingThresholdTemperature() {
        temperatureCallback = null;
    }

    public HomekitCharacteristicChangeCallback getTemperatureCallback() {
        return temperatureCallback;
    }
}
