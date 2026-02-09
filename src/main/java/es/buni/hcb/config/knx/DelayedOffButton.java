package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DelayedOffButton extends Button {
    private final GroupAddress controlAddress;
    private final int delayMinutes;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public DelayedOffButton(KNXAdapter adapter, String location, String id,
                            int mainGroup, int middleGroup, int subGroup,
                            GroupAddress controlAddress) {
        this(adapter, location, id, mainGroup, middleGroup, subGroup, controlAddress, 30);
    }

    public DelayedOffButton(KNXAdapter adapter, String location, String id,
                            int mainGroup, int middleGroup, int subGroup,
                            GroupAddress controlAddress, int delayMinutes) {
        super(
                adapter, location, id,
                mainGroup, middleGroup, subGroup
        );

        this.controlAddress = controlAddress;
        this.delayMinutes = delayMinutes;
    }

    @Override
    protected void onButtonPressed() {
        try {
            Logger.info(getNamedId() + " executing in " + delayMinutes + " minutes.");
            scheduler.schedule(() -> {
                try {
                    adapter.communicator().write(controlAddress, false);
                    Logger.info("Delayed off executed for " + controlAddress);
                } catch (Exception e) {
                    Logger.error("Failed to execute delayed off: " + e.getMessage());
                }
            }, delayMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            Logger.error("Failed to activated delayed off: " + e.getMessage());
        }
    }
}
