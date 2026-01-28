package es.buni.hcb.adapters.knx.entities.sensor;

import es.buni.hcb.adapters.knx.KNXAdapter;
import io.github.hapjava.accessories.OccupancySensorAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedEnum;

import java.util.concurrent.CompletableFuture;

public class OccupancySensor extends BinarySensor implements OccupancySensorAccessory {
    public OccupancySensor(KNXAdapter adapter, String location, String id, int stateMainGroup, int stateMiddleGroup, int stateSubGroup) {
        super(adapter, location, id, stateMainGroup, stateMiddleGroup, stateSubGroup);
    }

    @Override
    protected void onStateChanged(boolean newValue) {
        super.onStateChanged(newValue);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    @Override
    public CompletableFuture<OccupancyDetectedEnum> getOccupancyDetected() {
        return CompletableFuture.completedFuture(getState()
                        ? OccupancyDetectedEnum.DETECTED
                        : OccupancyDetectedEnum.NOT_DETECTED
        );
    }

    @Override
    public void subscribeOccupancyDetected(HomekitCharacteristicChangeCallback callback) {
        this.subscribeCallback = callback;
    }

    @Override
    public void unsubscribeOccupancyDetected() {
        this.subscribeCallback = null;
    }
}
