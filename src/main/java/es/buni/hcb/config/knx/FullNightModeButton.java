package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.config.KNXEntities;
import es.buni.hcb.utils.Logger;
import io.calimero.process.ProcessCommunication;

public final class FullNightModeButton extends Button {
    public FullNightModeButton(KNXAdapter adapter, String location, String id) {
        super(adapter, location, id, 0, 0, 11);
        adapter.declareCommand(getNamedId(), "scene", KNXEntities.SCENE_RECALL_GROUP_ADDRESS, "18.001");
    }
    @Override protected void onButtonPressed() {
        if (!(adapter.getRegistry().get("livingroom.toggle.nightlighting") instanceof Toggle master) || !master.isStateKnown()) return;
        boolean enable = !master.isOn();
        for (String room : new String[]{"livingroom", "bedroom.north", "bedroom.south", "bathroom"}) {
            if (adapter.getRegistry().get(room + ".toggle.nightlighting") instanceof Toggle toggle) {
                try { toggle.setSwitchState(enable); }
                catch (Exception e) { Logger.error("Night mode update failed for " + room, e); return; }
            }
        }
        if (enable) try {
            adapter.bus().write(KNXEntities.SCENE_RECALL_GROUP_ADDRESS,
                    ScenesEnum.LIVINGROOM_NIGHT.getSceneNumber(), ProcessCommunication.UNSCALED);
        } catch (Exception e) { Logger.error("Night scene failed", e); }
    }
}
