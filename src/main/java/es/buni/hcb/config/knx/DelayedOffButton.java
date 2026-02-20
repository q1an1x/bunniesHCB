package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayedOffButton extends Button {
    private final Set<GroupAddress> groupAddresses;
    private final int delayMinutes;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DelayedOffButton(KNXAdapter adapter, String location, String id,
                            int mainGroup, int middleGroup, int subGroup,
                            Set<GroupAddress> groupAddresses) {
        this(adapter, location, id, mainGroup, middleGroup, subGroup, groupAddresses, 30);
    }

    public DelayedOffButton(KNXAdapter adapter, String location, String id,
                            int mainGroup, int middleGroup, int subGroup,
                            Set<GroupAddress> groupAddresses,
                            int delayMinutes) {
        super(
                adapter, location, id,
                mainGroup, middleGroup, subGroup
        );

        this.groupAddresses = groupAddresses;
        this.delayMinutes = delayMinutes;
    }

    @Override
    protected void onButtonPressed() {
        try {
            Logger.info(getNamedId() + " executing in " + delayMinutes + " minutes.");
            scheduler.schedule(() -> {
                try {
                    for  (GroupAddress groupAddress : groupAddresses) {
                        adapter.communicator().write(groupAddress, false);
                        Logger.info("Delayed off executed for " + groupAddress);
                    }
                } catch (Exception e) {
                    Logger.error("Failed to execute delayed off: " + e.getMessage());
                }
            }, delayMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            Logger.error("Failed to activated delayed off: " + e.getMessage());
        }
    }
}
