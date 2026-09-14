package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;

import java.util.Set;

public abstract class Button extends KNXEntity {
    private final GroupAddress listeningGroupAddress;

    protected Button(KNXAdapter adapter, String location, String id,
                     int listeningMainGroup, int listeningMiddleGroup, int listeningSubGroup) {
        super(adapter, location, id);

        listeningGroupAddress = new GroupAddress(listeningMainGroup, listeningMiddleGroup, listeningSubGroup);
    }

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                listeningGroupAddress
        );
    }

    @Override
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        return java.util.List.of(binding("press", listeningGroupAddress, "1.001", es.buni.hcb.adapters.knx.KnxBinding.Role.EVENT));
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        return event.getServiceCode() == 0x80 && event.getASDU().length == 1;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
        if (adapter.isAutomationReady()) onButtonPressed();
    }

    protected abstract void onButtonPressed();
}
