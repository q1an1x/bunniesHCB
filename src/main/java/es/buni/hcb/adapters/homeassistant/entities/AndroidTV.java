package es.buni.hcb.adapters.homeassistant.entities;

import com.google.gson.JsonObject;
import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.utils.Logger;

public class AndroidTV extends HomeAssistantEntity {

    private String state = "unknown";
    private String currentApp = "unknown";
    private double volume = 0.0;

    public AndroidTV(HomeAssistantAdapter adapter, String location, String id, String haEntityId) {
        super(adapter, location, id, haEntityId);
    }

    @Override
    public void onStateChanged(JsonObject newState) {
        String oldState = this.state;

        if (newState.has("state") && !newState.get("state").isJsonNull()) {
            this.state = newState.get("state").getAsString();
        }

        if (newState.has("attributes")) {
            JsonObject attrs = newState.get("attributes").getAsJsonObject();

            if (attrs.has("app_name") && !attrs.get("app_name").isJsonNull()) {
                this.currentApp = attrs.get("app_name").getAsString();
            }

            if (attrs.has("volume_level") && !attrs.get("volume_level").isJsonNull()) {
                this.volume = attrs.get("volume_level").getAsDouble();
            }
        }

        if (!this.state.equals(oldState)) {
            Logger.info(String.format("Android TV '%s' Changed: %s -> %s (App: %s)",
                    getNamedId(), oldState, state, currentApp));
        }
    }

    public void turnOn() {
        Logger.info("Command: Android TV On");
        callService("media_player", "turn_on");
    }

    public void turnOff() {
        Logger.info("Command: Android TV Off");
        callService("media_player", "turn_off");
    }

    public void play() {
        Logger.info("Command: Android TV Play");
        callService("media_player", "media_play");
    }

    public void pause() {
        Logger.info("Command: Android TV Pause");
        callService("media_player", "media_pause");
    }

    public void playPause() {
        callService("media_player", "media_play_pause");
    }

    public boolean isPlaying() {
        return "playing".equalsIgnoreCase(state);
    }

    public boolean isOn() {
        return !"off".equalsIgnoreCase(state) && !"unavailable".equalsIgnoreCase(state);
    }

    public String getCurrentApp() {
        return currentApp;
    }
}