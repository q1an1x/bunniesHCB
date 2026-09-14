package es.buni.hcb.adapters.knx.entities.sensor;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.util.Set;

public class BinarySensor extends KNXEntity {

    protected final GroupAddress stateAddress;
    private volatile boolean state;
    private final boolean invert;

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                stateAddress
        );
    }

    @Override
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        return java.util.List.of(binding("state", stateAddress, "1.002", es.buni.hcb.adapters.knx.KnxBinding.Role.SENSOR));
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
        boolean newState = invert != ProcessListener.asBool(event);
        boolean changed = !known(address) || newState != state;
        state = newState;
        observed(address);
        return changed;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        onStateChanged(state);
        publishBusState("state", state, event);
    }

    protected void onStateChanged(boolean newValue) {
        Logger.info("Sensor " + getNamedId() + " state changed to " + newValue);

        if (subscribeCallback != null) {
            subscribeCallback.changed();
        }
    }

    @Override
    public void initialize() throws Exception {
        this.readState();

        super.initialize();
    }

    private void readState() throws Exception {
        state = invert != adapter.bus().readBool(stateAddress);
        observed(stateAddress);
    }

    @Override
    public String toString() {
        return super.toString() + ", "
                + "state: " + getState();
    }
}
