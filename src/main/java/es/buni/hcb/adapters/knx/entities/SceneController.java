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
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        publishEvent(SceneRecalledEvent.of(getNamedId(), ProcessListener.asUnsigned(event, ProcessCommunication.UNSCALED)));
        return true;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
    }
}
