package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import io.github.hapjava.accessories.optionalcharacteristic.AccessoryWithBrightness;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class Dimmable extends Light implements AccessoryWithBrightness {
    private static final long DIMMING_TIME_MS = 3000;

    private volatile long brightnessLastSetAt = 0;

    protected final GroupAddress dimmingValueAddress;
    protected final GroupAddress statusDimmingValueAddress;
    protected final GroupAddress dimmingValueTimeAddress;

    private volatile int brightness;

    private HomekitCharacteristicChangeCallback brightnessCallback;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                switchAddress,
                statusSwitchAddress,
                dimmingValueAddress,
                statusDimmingValueAddress,
                dimmingValueTimeAddress
        );
    }

    public int getBrightnessValue() {
        return brightness;
    }

    public void setBrightnessValue(int brightness) throws Exception {
        this.brightness = brightness;
        this.brightnessLastSetAt = System.currentTimeMillis();
        writeBrightness(brightness);
    }

    public static Dimmable fromConvention(KNXAdapter adapter, String location, String id,
                                          int switchMainGroup, int switchMiddleGroup, int switchSubGroup)
            throws Exception {
        return new Dimmable(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 2,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 1,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 3,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 5
        );
    }

    public Dimmable(KNXAdapter adapter, String location, String id,
                    int switchMainGroup, int switchMiddleGroup, int switchSubGroup,
                    int statusSwitchMainGroup, int statusSwitchMiddleGroup, int statusSwitchSubGroup,
                    int dimmingValueMainGroup, int dimmingValueMiddleGroup, int dimmingValueSubGroup,
                    int statusDimmingValueMainGroup, int statusDimmingValueMiddleGroup, int statusDimmingValueSubGroup,
                    int dimmingValueTimeMainGroup, int dimmingValueTimeMiddleGroup, int dimmingValueTimeSubGroup)
            throws Exception {
        super(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                statusSwitchMainGroup, statusSwitchMiddleGroup, statusSwitchSubGroup);

        dimmingValueAddress = new GroupAddress(dimmingValueMainGroup, dimmingValueMiddleGroup, dimmingValueSubGroup);
        statusDimmingValueAddress = new GroupAddress(statusDimmingValueMainGroup, statusDimmingValueMiddleGroup, statusDimmingValueSubGroup);
        dimmingValueTimeAddress = new GroupAddress(dimmingValueTimeMainGroup, dimmingValueTimeMiddleGroup, dimmingValueTimeSubGroup);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(statusDimmingValueAddress) || address.equals(dimmingValueAddress)) {
            if (System.currentTimeMillis() - brightnessLastSetAt < DIMMING_TIME_MS) {
                return false;
            }

            int newState = ProcessListener.asUnsigned(event, ProcessCommunication.SCALING);
            if (newState != brightness) {
                brightness = newState;
                return true;
            }
        }

        return super.updateState(address, event);
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        super.onStateUpdated(address, event);

        if (address.equals(statusDimmingValueAddress) || address.equals(dimmingValueAddress)) {
            onBrightnessChanged(brightness);
            publishStateChanged("brightness", brightness);
        }
    }

    protected void onBrightnessChanged(int newValue) {
        Logger.info(getNamedId() + " brightness changed to " + newValue);

        if (brightnessCallback != null) {
            brightnessCallback.changed();
        }
    }

    @Override
    public void initialize() throws Exception {
        readBrightness();

        super.initialize();
    }

    private void readBrightness() throws Exception {
        brightness = adapter.communicator().readUnsigned(statusDimmingValueAddress, ProcessCommunication.SCALING);
    }

    private void writeBrightness(int brightness) throws Exception {
        adapter.communicator().write(dimmingValueAddress, brightness, ProcessCommunication.SCALING);
    }

    @Override
    public String toString() {
        return super.toString() + ", "
                + "brightness: " + brightness;
    }

    @Override
    public CompletableFuture<Integer> getBrightness() {
        return CompletableFuture.completedFuture(getBrightnessValue());
    }

    @Override
    public CompletableFuture<Void> setBrightness(Integer value) throws Exception {
        setBrightnessValue(value);
        Logger.info("HomeKit set " + getNamedId() + " brightness to " + brightness);
        return null;
    }

    @Override
    public void subscribeBrightness(HomekitCharacteristicChangeCallback callback) {
        brightnessCallback = callback;
    }

    @Override
    public void unsubscribeBrightness() {
        brightnessCallback = null;
    }
}
