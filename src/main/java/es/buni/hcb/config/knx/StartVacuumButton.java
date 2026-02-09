package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.homeassistant.entities.VacuumRobot;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.core.Entity;
import es.buni.hcb.utils.Logger;

public class StartVacuumButton extends Button {

    private static final String TARGET_VACUUM_ID = "livingroom.vacuum";

    public StartVacuumButton(KNXAdapter adapter) {
        super(
                adapter, "livingroom", "button.startVacuum",
                2, 0, 101
        );
    }

    @Override
    protected void onButtonPressed() {
        Entity entity = adapter.getRegistry().get(TARGET_VACUUM_ID);

        if (entity instanceof VacuumRobot vacuum) {
            Logger.info("KNX button: starting vacuum " + TARGET_VACUUM_ID);

            if (! vacuum.isCleaning()) {
                vacuum.startCleaning();
            }
        }
    }
}