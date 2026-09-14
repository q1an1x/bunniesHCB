package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.core.events.SceneRecalledEvent;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessCommunication;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.util.Set;

public class SceneController extends KNXEntity {
    private final GroupAddress groupAddress;

    public SceneController(
            KNXAdapter adapter,
            int mainGroup, int middleGroup, int subGroup) {
        super(adapter, "system", "scenecontroller");
        groupAddress = new GroupAddress(mainGroup, middleGroup, subGroup);
    }

    @Override
    public Set<GroupAddress> groupAddresses() {
        return Set.of(
                groupAddress
        );
    }

    @Override
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        return java.util.List.of(binding("sceneRecall", groupAddress, "18.001", es.buni.hcb.adapters.knx.KnxBinding.Role.EVENT));
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        if (event.getServiceCode() != 0x80 || event.getASDU().length != 1) return false;
        if (adapter.isLocalSource(event)) return false; // Software scene echoes are not manual scene selections.
        int value = ProcessListener.asUnsigned(event, ProcessCommunication.UNSCALED);
        if ((value & 0xc0) != 0) return false; // DPT 18: ignore store and reserved-bit frames.
        if (adapter.isAutomationReady()) publishEvent(SceneRecalledEvent.of(getNamedId(), value));
        return true;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
    }
}
