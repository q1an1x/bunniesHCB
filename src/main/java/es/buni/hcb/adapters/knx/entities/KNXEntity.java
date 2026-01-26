package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.core.Entity;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;

import java.util.Set;

public abstract class KNXEntity extends Entity {

    protected final KNXAdapter adapter;

    public abstract Set<GroupAddress> groupAddresses();

    protected KNXEntity(KNXAdapter adapter, String location, String id) {
        super(location, id);
        this.adapter = adapter;
    }

    public final void handleBusUpdate(GroupAddress address, ProcessEvent event) throws Exception {
        boolean changed = updateState(address, event);
        if (changed) {
            onStateUpdated(address, event);
        }
    }

    protected abstract boolean updateState(GroupAddress address, ProcessEvent event) throws Exception;

    protected abstract void onStateUpdated(GroupAddress address, ProcessEvent event);
}
