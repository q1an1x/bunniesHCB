package es.buni.hcb.config;

import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.adapters.homeassistant.entities.VacuumRobot;

public class HomeAssistantEntities {
    public static void registerAll(HomeAssistantAdapter adapter) {
        adapter.register(new VacuumRobot(
                adapter, "livingroom", "vacuum", "vacuum.valetudo_vacuum"
        ));
    }
}
