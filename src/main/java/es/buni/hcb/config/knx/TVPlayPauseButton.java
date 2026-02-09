package es.buni.hcb.config.knx;

import es.buni.hcb.adapters.homeassistant.entities.AndroidTV;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Button;
import es.buni.hcb.core.Entity;
import es.buni.hcb.utils.Logger;

public class TVPlayPauseButton extends Button {

    private static final String TARGET_TV_ID = "bedroom.south.googletv";

    public TVPlayPauseButton(KNXAdapter adapter) {
        super(adapter, "bedroom.south", "button.googletv.playpause", 4, 0, 102);
    }

    @Override
    protected void onButtonPressed() {
        Entity entity = adapter.getRegistry().get(TARGET_TV_ID);

        if (entity instanceof AndroidTV) {
            AndroidTV tv = (AndroidTV) entity;
            Logger.info("KNX button: Toggling Play/Pause on TV");

            tv.playPause();
        } else {
            Logger.warn("KNX button: TV Play/Pause Button pressed, but '" + TARGET_TV_ID + "' not found.");
        }
    }
}