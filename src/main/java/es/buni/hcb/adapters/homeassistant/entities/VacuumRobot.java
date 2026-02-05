package es.buni.hcb.adapters.homeassistant.entities;

import com.google.gson.JsonObject;
import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.utils.Logger;

public class VacuumRobot extends HomeAssistantEntity {

    private String state = "unknown";

    public VacuumRobot(HomeAssistantAdapter adapter, String location, String id, String haEntityId) {
        super(adapter, location, id, haEntityId);
    }

    @Override
    public void initialize() {
        Logger.info("Vacuum robot initialized: " + getNamedId());
    }

    public void startCleaning() {
        Logger.info("Command: Vacuum Start");
        callService("vacuum", "start");
    }

    @Override
    public void onStateChanged(JsonObject newState) {
        String oldState = this.state;

        if (newState.has("state")) {
            this.state = newState.get("state").getAsString();
        }

        if (newState.has("attributes")) {
            JsonObject attrs = newState.get("attributes").getAsJsonObject();
        }

        if (!this.state.equals(oldState)) {
            Logger.info(String.format("Vacuum '%s' State: %s -> %s",
                    getNamedId(), oldState, state));
        }
    }

    public boolean isCleaning() {
        return "cleaning".equalsIgnoreCase(state);
    }
}