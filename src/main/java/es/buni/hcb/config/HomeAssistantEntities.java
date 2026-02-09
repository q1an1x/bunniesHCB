package es.buni.hcb.config;

import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.adapters.homeassistant.entities.AndroidTV;
import es.buni.hcb.adapters.homeassistant.entities.VacuumRobot;

public class HomeAssistantEntities {
    public static void registerAll(HomeAssistantAdapter adapter) {
        adapter.register(new VacuumRobot(
                adapter, "livingroom", "vacuum", "vacuum.valetudo_vacuum"
        ));

        adapter.register(new AndroidTV(
                adapter,
                "bedroom.south",
                "googletv",
                "media_player.google_tv_2"
        ));;
    }
}
