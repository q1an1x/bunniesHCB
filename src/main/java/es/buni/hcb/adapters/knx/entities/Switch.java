package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.KnxBinding;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import java.util.List;
import java.util.Set;

public class Switch extends KNXEntity {
    protected final GroupAddress switchAddress;
    protected final GroupAddress statusSwitchAddress;
    private volatile boolean on;

    @Override public Set<GroupAddress> groupAddresses() { return Set.of(switchAddress, statusSwitchAddress); }
    @Override public List<KnxBinding> bindings() {
        return List.of(binding("switch", switchAddress, "1.001", KnxBinding.Role.COMMAND),
                binding("switchStatus", statusSwitchAddress, "1.001", KnxBinding.Role.STATUS));
    }
    public boolean isOn() { return on; }
    public boolean hasSwitchState() { return known(statusSwitchAddress); }
    public void toggle() throws Exception {
        if (!hasSwitchState()) throw new IllegalStateException("Cannot toggle unknown state: " + getNamedId());
        setSwitchState(!on);
    }
    public void on() throws Exception { setSwitchState(true); }
    public void off() throws Exception { setSwitchState(false); }

    public static Switch fromConvention(KNXAdapter adapter, String location, String id, int main, int middle, int sub) throws Exception {
        return new Switch(adapter, location, id, main, middle, sub, main, middle, sub + 1);
    }
    public Switch(KNXAdapter adapter, String location, String id, int main, int middle, int sub,
                  int statusMain, int statusMiddle, int statusSub) {
        super(adapter, location, id);
        switchAddress = new GroupAddress(main, middle, sub);
        statusSwitchAddress = new GroupAddress(statusMain, statusMiddle, statusSub);
    }
    @Override protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        // A command and a link-layer acknowledgement are not actuator feedback.
        if (!address.equals(statusSwitchAddress)) return false;
        boolean value = ProcessListener.asBool(event);
        boolean changed = !known(address) || value != on;
        on = value;
        observed(address);
        return changed;
    }
    @Override protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        if (address.equals(statusSwitchAddress)) {
            onSwitchStatusChanged(on);
            publishBusState("switch", on, event);
        }
    }
    protected void onSwitchStatusChanged(boolean value) { }
    @Override public void initialize() throws Exception {
        on = adapter.bus().readBool(statusSwitchAddress);
        observed(statusSwitchAddress);
        super.initialize();
    }
    private void setSwitchState(boolean state) throws Exception { adapter.bus().write(switchAddress, state); }
}
