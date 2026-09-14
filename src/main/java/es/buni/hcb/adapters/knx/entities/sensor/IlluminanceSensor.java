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

    private volatile double illuminance;

    @Override
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        return java.util.List.of(binding("illuminance", illuminanceValueGroupAddress, "9.004", es.buni.hcb.adapters.knx.KnxBinding.Role.SENSOR));
    }

    @Override public void initialize() throws Exception {
        readIlluminanceValue();
        super.initialize();
    }

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

        if (!Double.isFinite(calibrationFactor) || calibrationFactor <= 0)
            throw new IllegalArgumentException("Calibration must be positive");
        this.calibrationFactor = calibrationFactor;
    }

    private void writeIlluminanceValue(double illuminance) throws Exception {
        throw new UnsupportedOperationException("Illuminance is a read-only sensor");
    }

    private void readIlluminanceValue() throws Exception {
        double rawIlluminance = adapter.bus().readFloat(illuminanceValueGroupAddress);
        illuminance = validated(rawIlluminance * calibrationFactor);
        observed(illuminanceValueGroupAddress);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        double rawIlluminance = ProcessListener.asFloat(event);
        double calibratedIlluminance = validated(rawIlluminance * calibrationFactor);
        boolean first = !known(address);
        observed(address);

        if (first || illuminance != calibratedIlluminance) {
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
        publishBusState("state", illuminance, event);
    }

    protected void onStateChanged(double newValue) {
        Logger.info("Sensor " + getNamedId() + " illuminance changed to " + newValue);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    private static double validated(double lux) {
        if (!Double.isFinite(lux) || lux < 0) throw new IllegalArgumentException("Invalid illuminance");
        return lux;
    }

    @Override
    public String toString() {
        return super.toString()
                + ", illuminance: " + illuminance;
    }

    @Override
    public CompletableFuture<Double> getCurrentAmbientLightLevel() {
        return stateFuture(illuminanceValueGroupAddress, Math.max(0.0001, Math.min(100000, getIlluminance())));
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
