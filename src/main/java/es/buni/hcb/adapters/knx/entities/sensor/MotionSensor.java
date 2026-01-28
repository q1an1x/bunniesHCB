package es.buni.hcb.adapters.knx.entities.sensor;

import es.buni.hcb.adapters.knx.KNXAdapter;
import io.github.hapjava.accessories.MotionSensorAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.concurrent.CompletableFuture;

public class MotionSensor extends BinarySensor implements MotionSensorAccessory {
    public MotionSensor(KNXAdapter adapter, String location, String id, int stateMainGroup, int stateMiddleGroup, int stateSubGroup) {
        super(adapter, location, id, stateMainGroup, stateMiddleGroup, stateSubGroup);
    }

    @Override
    public CompletableFuture<Boolean> getMotionDetected() {
        return CompletableFuture.completedFuture(getState());
    }

    @Override
    public void subscribeMotionDetected(HomekitCharacteristicChangeCallback callback) {
        this.subscribeCallback = callback;
    }

    @Override
    public void unsubscribeMotionDetected() {
        this.subscribeCallback = null;
    }
}
