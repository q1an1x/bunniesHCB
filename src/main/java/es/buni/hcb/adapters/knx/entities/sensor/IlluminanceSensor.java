package es.buni.hcb.adapters.knx.entities.sensor;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import io.github.hapjava.accessories.LightSensorAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class IlluminanceSensor extends KNXEntity implements LightSensorAccessory {
    private final GroupAddress illuminanceValueGroupAddress;
    private final double calibrationFactor;

    private double illuminance;

    public double getIlluminance() {
        return this.illuminance;
    }

    public void setIlluminance(double illuminance) throws Exception {
        writeIlluminanceValue(illuminance);
    }

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                illuminanceValueGroupAddress
        );
    }

    public IlluminanceSensor(
            KNXAdapter adapter, String location, String id,
            int illuminanceValueMainGroup, int illuminanceValueMiddleGroup, int illuminanceValueSubGroup
    ) {
        this(adapter, location, id,
                illuminanceValueMainGroup, illuminanceValueMiddleGroup, illuminanceValueSubGroup,
                1.0
        );
    }

    public IlluminanceSensor(
            KNXAdapter adapter, String location, String id,
            int illuminanceValueMainGroup, int illuminanceValueMiddleGroup, int illuminanceValueSubGroup,
            double calibrationFactor
    ) {
        super(adapter, location, id);

        this.illuminanceValueGroupAddress = new GroupAddress(
                illuminanceValueMainGroup, illuminanceValueMiddleGroup, illuminanceValueSubGroup
        );

        this.calibrationFactor = calibrationFactor;
    }

    private void writeIlluminanceValue(double illuminance) throws Exception {
        this.illuminance = illuminance;
        adapter.communicator().write(illuminanceValueGroupAddress, illuminance, false);
    }

    private void readIlluminanceValue() throws Exception {
        double rawIlluminance = adapter.communicator().readFloat(illuminanceValueGroupAddress);
        illuminance = rawIlluminance * calibrationFactor;
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        double rawIlluminance = ProcessListener.asFloat(event);
        double calibratedIlluminance = rawIlluminance * calibrationFactor;

        if (illuminance != calibratedIlluminance) {
            this.illuminance = calibratedIlluminance;

            Logger.info(
                    "Sensor " + getNamedId()
                            + " raw lux=" + rawIlluminance
                            + ", factor=" + calibrationFactor
                            + ", calibrated lux=" + calibratedIlluminance
            );

            return true;
        }
        return false;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        onStateChanged(illuminance);
    }

    protected void onStateChanged(double newValue) {
        Logger.info("Sensor " + getNamedId() + " illuminance changed to " + newValue);
        publishStateChanged(newValue);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    @Override
    public String toString() {
        return super.toString()
                + ", illuminance: " + illuminance;
    }

    @Override
    public CompletableFuture<Double> getCurrentAmbientLightLevel() {
        return CompletableFuture.completedFuture(getIlluminance());
    }

    @Override
    public void subscribeCurrentAmbientLightLevel(HomekitCharacteristicChangeCallback callback) {
        subscribeCallback = callback;
    }

    @Override
    public void unsubscribeCurrentAmbientLightLevel() {
        subscribeCallback = null;
    }
}
