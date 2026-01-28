package es.buni.hcb.adapters.knx.entities.sensor;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.util.Set;

public class BinarySensor extends KNXEntity {

    private final GroupAddress stateAddress;
    private volatile boolean state;
    private final boolean invert;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                stateAddress
        );
    }

    public boolean getState() {
        return state;
    }

    public BinarySensor(KNXAdapter adapter, String location, String id,
                        int stateMainGroup, int stateMiddleGroup, int stateSubGroup) {
        this(adapter, location, id, stateMainGroup, stateMiddleGroup, stateSubGroup, false);
    }

    public BinarySensor(KNXAdapter adapter, String location, String id,
                           int stateMainGroup, int stateMiddleGroup, int stateSubGroup, boolean invert) {
        super(adapter, location, id);

        this.invert = invert;
        stateAddress = new GroupAddress(stateMainGroup, stateMiddleGroup, stateSubGroup);
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (address.equals(stateAddress)) {
            boolean newState = invert != ProcessListener.asBool(event);
            if (newState != state) {
                state = newState;
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        onStateChanged(state);
    }

    protected void onStateChanged(boolean newValue) {
        Logger.info("Sensor " + getNamedId() + " state changed to " + newValue);
        publishStateChanged(state);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    @Override
    public void initialize() throws Exception {
        this.readState();
    }

    private void readState() throws Exception {
        state = invert != adapter.communicator().readBool(stateAddress);
    }
}
