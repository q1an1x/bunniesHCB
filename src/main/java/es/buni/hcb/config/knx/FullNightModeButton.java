package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.config.KNXEntities;
import es.buni.hcb.utils.Logger;
import io.calimero.process.ProcessCommunication;

public class FullNightModeButton extends Button {
    public FullNightModeButton(KNXAdapter adapter, String location, String id) {
        super(
                adapter, location, id,
                0, 0, 11
        );
    }

    @Override
    protected void onButtonPressed() {
        try {
            Toggle toggle = (Toggle) adapter.getRegistry().get("livingroom.toggle.nightlighting");
            if (toggle != null) {
                if (toggle.isOn()) {
                    toggle.setSwitchState(false);
                    return;
                }
                toggle.setSwitchState(true);
            }
            toggle = (Toggle) adapter.getRegistry().get("bedroom.north.toggle.nightlighting");
            if (toggle != null) {
                toggle.setSwitchState(true);
            }
            toggle = (Toggle) adapter.getRegistry().get("bedroom.south.toggle.nightlighting");
            if (toggle != null) {
                toggle.setSwitchState(true);
            }
            toggle = (Toggle) adapter.getRegistry().get("bathroom.toggle.nightlighting");
            if (toggle != null) {
                toggle.setSwitchState(true);
            }

            adapter.communicator().write(
                    KNXEntities.SCENE_RECALL_GROUP_ADDRESS,
                    3,
                    ProcessCommunication.UNSCALED
            );
        } catch (Exception e) {
            Logger.error("Failed to turn on full night mode: " + e.getMessage());
        }
    }
}
