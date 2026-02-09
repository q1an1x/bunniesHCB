package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.homeassistant.entities.AndroidTV;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.core.Entity;
import es.buni.hcb.utils.Logger;

public class TVPowerButton extends Button {

    private static final String TARGET_TV_ID = "bedroom.south.googletv";

    public TVPowerButton(KNXAdapter adapter) {
        super(adapter, "bedroom.south", "button.googletv.power", 4, 0, 101);
    }

    @Override
    protected void onButtonPressed() {
        Entity entity = adapter.getRegistry().get(TARGET_TV_ID);

        if (entity instanceof AndroidTV) {
            AndroidTV tv = (AndroidTV) entity;

            if (tv.isOn()) {
                Logger.info("KNX button: Toggling TV OFF");
                tv.turnOff();
            } else {
                Logger.info("KNX button: Toggling TV ON");
                tv.turnOn();
            }
        } else {
            Logger.warn("KNX button: TV Power Button pressed, but '" + TARGET_TV_ID + "' not found.");
        }
    }
}