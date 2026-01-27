package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.util.Set;

public class Dimmable extends Light {

    protected final GroupAddress dimmingValueAddress;
    protected final GroupAddress statusDimmingValueAddress;
    protected final GroupAddress dimmingValueTimeAddress;

    private int brightness;

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

    public int getBrightness() {
        return brightness;
    }

    public void setBrightness(int brightness) throws Exception {
        this.brightness = brightness;
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
        Logger.info(getId() + " brightness changed to " + newValue);
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

}
