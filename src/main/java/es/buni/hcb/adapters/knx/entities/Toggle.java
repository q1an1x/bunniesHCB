package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.KnxBinding;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;
import io.github.hapjava.accessories.SwitchAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Software-owned automation state; unlike a light, it has no independent actuator feedback. */
public class Toggle extends KNXEntity implements SwitchAccessory {
    private final GroupAddress address;
    private volatile boolean on;
    private volatile HomekitCharacteristicChangeCallback callback;

    public Toggle(KNXAdapter adapter, String location, String id, int main, int middle, int sub) {
        super(adapter, location, id);
        address = new GroupAddress(main, middle, sub);
    }
    public boolean isOn() { return known(address) && on; }
    @Override public Set<GroupAddress> groupAddresses() { return Set.of(address); }
    @Override public List<KnxBinding> bindings() {
        return List.of(binding("enabled", address, "1.001", KnxBinding.Role.SOFTWARE_STATE));
    }
    @Override public synchronized void initialize() throws Exception {
        try { on = adapter.bus().readBool(address); observed(address); }
        catch (Exception e) { forget(address); throw e; }
        super.initialize();
    }
    @Override protected synchronized boolean updateState(GroupAddress ga, ProcessEvent event) throws Exception {
        on = ProcessListener.asBool(event);
        observed(address);
        // Repeated explicit OFF still means a manual decision (e.g. cancel a restore timer).
        return true;
    }
    @Override protected void onStateUpdated(GroupAddress ga, ProcessEvent event) {
        if (callback != null) callback.changed();
        publishBusState("state", on, event);
    }
    @Override public CompletableFuture<Boolean> getSwitchState() { return stateFuture(address, on); }
    @Override public CompletableFuture<Void> setSwitchState(boolean value) throws Exception {
        return setState(value, es.buni.hcb.core.events.StateChangedEvent.Origin.LOCAL);
    }
    public CompletableFuture<Void> setAutomationState(boolean value) throws Exception {
        return setState(value, es.buni.hcb.core.events.StateChangedEvent.Origin.AUTOMATION);
    }
    private CompletableFuture<Void> setState(boolean value, es.buni.hcb.core.events.StateChangedEvent.Origin origin) throws Exception {
        long epoch = adapter.processingGeneration();
        synchronized (this) {
            adapter.bus().write(address, value);
            on = value;
            observed(address, epoch);
        }
        if (callback != null) callback.changed();
        publishEvent(new es.buni.hcb.core.events.StateChangedEvent(getNamedId(), "state", value, adapter.clock().millis(), origin), epoch);
        return CompletableFuture.completedFuture(null);
    }
    @Override public void subscribeSwitchState(HomekitCharacteristicChangeCallback value) { callback = value; }
    @Override public void unsubscribeSwitchState() { callback = null; }
}
