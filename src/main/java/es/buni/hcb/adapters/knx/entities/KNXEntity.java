package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.KnxBinding;
import es.buni.hcb.core.Entity;
import es.buni.hcb.core.events.StateChangedEvent;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;

import es.buni.hcb.core.events.EntityEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class KNXEntity extends Entity {
    protected final KNXAdapter adapter;
    private final Map<GroupAddress, Long> observed = new ConcurrentHashMap<>();

    public abstract Set<GroupAddress> groupAddresses();
    public List<KnxBinding> bindings() { return List.of(); }

    protected KNXEntity(KNXAdapter adapter, String location, String id) {
        super(adapter, location, id);
        this.adapter = adapter;
    }

    protected KnxBinding binding(String property, GroupAddress address, String dpt, KnxBinding.Role role) {
        return new KnxBinding(getNamedId(), property, address, dpt, role);
    }

    protected final void observed(GroupAddress address) { observed(address, adapter.processingGeneration()); }
    protected final void observed(GroupAddress address, long epoch) { observed.put(address, epoch); }
    protected final boolean known(GroupAddress address) { return Objects.equals(observed.get(address), adapter.generation()); }
    @Override public void publishEvent(EntityEvent event) { publishEvent(event, adapter.processingGeneration()); }
    protected final void publishEvent(EntityEvent event, long epoch) {
        getEventBus().publish(event, action -> adapter.runInSession(epoch, action));
    }
    protected final void forget(GroupAddress address) { observed.remove(address); }
    public void invalidateState() { observed.clear(); }
    public boolean isStateKnown() {
        return bindings().stream().filter(KnxBinding::readable).allMatch(b -> known(b.address()));
    }

    protected final <T> CompletableFuture<T> stateFuture(GroupAddress address, T value) {
        return known(address) ? CompletableFuture.completedFuture(value)
                : CompletableFuture.failedFuture(new IllegalStateException("State unavailable: " + getNamedId()));
    }

    protected final void publishBusState(String property, Object value, ProcessEvent event) {
        var origin = event.getServiceCode() == 0x40 ? StateChangedEvent.Origin.BUS_RESPONSE
                : adapter.isLocalSource(event) ? StateChangedEvent.Origin.BUS_ECHO : StateChangedEvent.Origin.BUS_WRITE;
        publishEvent(new StateChangedEvent(getNamedId(), property, value, adapter.clock().millis(), origin));
    }

    public final void handleBusUpdate(GroupAddress address, ProcessEvent event) throws Exception {
        if (event.getServiceCode() != 0x80 && event.getServiceCode() != 0x40) return;
        if (updateState(address, event)) onStateUpdated(address, event);
    }

    protected abstract boolean updateState(GroupAddress address, ProcessEvent event) throws Exception;
    protected abstract void onStateUpdated(GroupAddress address, ProcessEvent event);
}
