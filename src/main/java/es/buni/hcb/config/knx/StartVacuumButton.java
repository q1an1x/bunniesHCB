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
    public java.util.List<es.buni.hcb.adapters.knx.KnxBinding> bindings() {
        return java.util.List.of(binding("press", groupAddresses().iterator().next(), "3.007", es.buni.hcb.adapters.knx.KnxBinding.Role.EVENT));
    }

    @Override
    protected boolean updateState(io.calimero.GroupAddress address, io.calimero.process.ProcessEvent event) {
        if (event.getServiceCode() != 0x80 || event.getASDU().length != 1) return false;
        int value = event.getASDU()[0] & 0xff;
        // ETS uses Button B2's dimming object. The release/stop nibble must not start cleaning.
        return value <= 0x0f && (value & 7) != 0;
    }

    @Override
    protected void onButtonPressed() {
        Entity entity = adapter.getRegistry().get(TARGET_VACUUM_ID);

        if (entity instanceof VacuumRobot vacuum) {
            Logger.info("KNX button: starting vacuum " + TARGET_VACUUM_ID);

            if (vacuum.hasKnownState() && !vacuum.isCleaning()) {
                vacuum.startCleaning();
            }
        }
    }
}