package es.buni.hcb.adapters.broadlink.oven;

import com.google.gson.JsonObject;

import java.time.LocalDateTime;

public class OvenCommandBuilder {
    private final JsonObject json;

    public static OvenCommandBuilder create() {
        LocalDateTime now = LocalDateTime.now();

        return ( new OvenCommandBuilder() ).setClockTime(
                now.getHour(),
                now.getMinute()
        );
    }

    public OvenCommandBuilder() {
        this.json = new JsonObject();
    }

    public OvenCommandBuilder setClockTime(int hour, int minute) {
        json.addProperty("clk_set_sw", 1);
        json.addProperty("clk_set_h", hour);
        json.addProperty("clk_set_m", minute);
        return this;
    }

    public OvenCommandBuilder setAlarm(int seconds) {
        json.addProperty("alarm_sw", 1);
        json.addProperty("alarm_tm", seconds);
        return this;
    }

    public boolean isModeSet() {
        return json.has("mu_seqno") &&  json.get("mu_seqno").getAsInt() != 0;
    }

    public OvenCommandBuilder setMode(OvenMode mode) {
        json.addProperty("mu_seqno", mode.ordinal());
        return this;
    }

    public OvenMode getMode() {
        return OvenMode.values()[json.get("mu_seqno").getAsInt()];
    }

    public boolean isTemperatureSet() {
        return json.has("mu_temp") && json.get("mu_temp").getAsInt() != 0;
    }

    public int getTemperature() {
        return json.get("mu_temp").getAsInt();
    }

    public OvenCommandBuilder setTemperature(int temp) {
        json.addProperty("mu_temp", temp);
        return this;
    }

    public boolean isSteamTemperatureSet() {
        return json.has("mu_steam_gr") && json.get("mu_steam_gr").getAsInt() != 0;
    }

    public int getSteamTemperature() {
        return json.get("mu_steam_gr").getAsInt();
    }

    public OvenCommandBuilder setSteamTemperature(int temp) {
        json.addProperty("mu_steam_gr", temp);
        return this;
    }

    public OvenCommandBuilder setSteamAssist(boolean state) {
        addBooleanProperty("steam_lv", state);
        return this;
    }

    public OvenCommandBuilder setDuration(int seconds) {
        json.addProperty("mu_heat_tm", seconds);
        return this;
    }

    public boolean isDurationSet() {
        return json.has("mu_heat_tm") &&  json.get("mu_heat_tm").getAsInt() != 0;
    }

    public OvenCommandBuilder setRunState(OvenCommandRunState state) {
        json.addProperty("run_sta", state.ordinal());
        return this;
    }

    public OvenCommandBuilder setChildLock(boolean state) {
        addBooleanProperty("cld_lock", state);
        return this;
    }

    public JsonObject build() {
        return json;
    }

    private void addBooleanProperty(String property, boolean value) {
        json.addProperty(property, value ? 1 : 0);
    }
}