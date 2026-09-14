package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.automation.modes.HouseMode;
import es.buni.hcb.utils.Logger;

/** The existing wall button and HomeKit use the same household-mode coordinator. */
public final class FullNightModeButton extends Button {
    public FullNightModeButton(KNXAdapter adapter, String location, String id) {
        super(adapter, location, id, 0, 0, 11);
    }
    @Override protected void onButtonPressed() {
        var modes = adapter.houseModes();
        if (modes == null) { Logger.warn("House modes are not configured"); return; }
        HouseMode target = modes.status().mode() == HouseMode.SLEEP ? HouseMode.HOME : HouseMode.SLEEP;
        modes.select(target).exceptionally(error -> { Logger.error("House sleep mode failed", error); return null; });
    }
}
