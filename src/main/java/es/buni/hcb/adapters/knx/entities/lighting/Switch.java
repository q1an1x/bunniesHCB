package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.util.Set;

public class Switch extends KNXEntity {

    protected final GroupAddress switchAddress;
    protected final GroupAddress statusSwitchAddress;

    private boolean on;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                switchAddress,
                statusSwitchAddress
        );
    }

    public boolean isOn() {
        return on;
    }

    public void toggle() throws Exception {
        if (on) {
            off();
        } else {
            on();
        }
    }

    public void on() throws Exception {
        setSwitchState(true);
    }

    public void off() throws Exception {
        setSwitchState(false);
    }

    public static Switch fromConvention(KNXAdapter adapter, String location, String id,
                                        int switchMainGroup, int switchMiddleGroup, int switchSubGroup)
    throws Exception {
        return new Switch(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 1);
    }

    public Switch(KNXAdapter adapter, String location, String id,
                     int switchMainGroup, int switchMiddleGroup, int switchSubGroup,
                     int statusSwitchMainGroup, int statusSwitchMiddleGroup, int statusSwitchSubGroup) {
        super(adapter, location, id);

        switchAddress = new GroupAddress(switchMainGroup, switchMiddleGroup, switchSubGroup);
        statusSwitchAddress = new GroupAddress(statusSwitchMainGroup, statusSwitchMiddleGroup, statusSwitchSubGroup);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(statusSwitchAddress) || address.equals(switchAddress)) {
            boolean newState = ProcessListener.asBool(event);
            if (newState != on) {
                on = newState;
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        if (address.equals(statusSwitchAddress) || address.equals(switchAddress)) {
            onSwitchStatusChanged(on);
            publishStateChanged("switch", on);
        }
    }

    protected void onSwitchStatusChanged(boolean newValue) {
        Logger.info(getId() + " turned " + (newValue ? "on" : "off"));
    }

    @Override
    public void initialize() throws Exception {
        this.readSwitch();

        super.initialize();
    }

    private void setSwitchState(boolean state) throws Exception {
        on = state;
        writeSwitch(state);
    }

    private void readSwitch() throws Exception {
        on = adapter.communicator().readBool(statusSwitchAddress);
    }

    private void writeSwitch(boolean state) throws Exception {
        adapter.communicator().write(switchAddress, state);
    }

    @Override
    public String toString() {
        return super.toString() + ", "
                + "on: " + on;
    }
}
