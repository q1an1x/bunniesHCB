package es.buni.hcb.adapters.broadlink.oven.homekit;

import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.adapters.broadlink.oven.OvenDoorState;
import io.github.hapjava.accessories.ContactSensorAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.contactsensor.ContactStateEnum;

import java.util.concurrent.CompletableFuture;

public class OvenHomeKitDoor extends OvenHomeKitComponent implements ContactSensorAccessory {
    protected HomekitCharacteristicChangeCallback doorStateCallback;

    public OvenHomeKitDoor(BSHOven oven) {
        super(oven, "door");
    }

    @Override
    public CompletableFuture<ContactStateEnum> getCurrentState() {
        return CompletableFuture.completedFuture(
                oven.getState().getDoorState() == OvenDoorState.OPEN
                        ? ContactStateEnum.NOT_DETECTED
                        : ContactStateEnum.DETECTED
        );
    }

    public HomekitCharacteristicChangeCallback getDoorStateCallback() {
        return doorStateCallback;
    }

    @Override
    public void subscribeContactState(HomekitCharacteristicChangeCallback callback) {
        this.doorStateCallback = callback;
    }

    @Override
    public void unsubscribeContactState() {
        this.doorStateCallback = null;
    }
}
