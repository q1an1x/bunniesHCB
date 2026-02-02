package es.buni.hcb.adapters.broadlink.oven;

import com.google.gson.JsonObject;

public class OvenState {
    private final boolean powerOn;
    private final OvenDoorState doorState;
    private final boolean waterTankInstalled;
    private final boolean waterTankFilled;

    private final OvenCleaningStatus cleaningStatus;
    private final OvenCookingMode cookingMode;
    private final OvenRunState runState;
    private final OvenMode mode;

    private final int temperature;
    private final int steamTemperature;

    private final int duration;
    private final int durationRemaining;

    private final boolean steamAssist;
    private final boolean childLock;

    private final OvenErrorEnum errorCode;

    public boolean isPowerOn() {
        return powerOn;
    }

    public OvenDoorState getDoorState() {
        return doorState;
    }

    public boolean isWaterTankInstalled() {
        return waterTankInstalled;
    }

    public boolean isWaterTankFilled() {
        return waterTankFilled;
    }

    public OvenCleaningStatus getCleaningStatus() {
        return cleaningStatus;
    }

    public OvenCookingMode getCookingMode() {
        return cookingMode;
    }

    public OvenRunState getRunState() {
        return runState;
    }

    public boolean isStandingBy() {
        return runState == OvenRunState.STANDBY;
    }

    public boolean isReady() {
        return runState == OvenRunState.READY;
    }

    public boolean isRunning() {
        return runState == OvenRunState.RUNNING;
    }

    public OvenMode getMode() {
        return mode;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getSteamTemperature() {
        return steamTemperature;
    }

    public int getDuration() {
        return duration;
    }

    public int getDurationRemaining() {
        return durationRemaining;
    }

    public boolean isSteamAssistOn() {
        return steamAssist;
    }

    public boolean isChildLockOn() {
        return childLock;
    }

    public OvenErrorEnum getError() {
        return errorCode;
    }

    public boolean hasError() {
        return getError() != OvenErrorEnum.NO_ERROR;
    }

    public static OvenState fromJsonObject(JsonObject jsonObject){
        return new OvenState(
                getBoolean(jsonObject, "power_sta"),
                OvenDoorState.values()[getInt(jsonObject, "dr_sta")],
                getBoolean(jsonObject, "water_tk_sta"),
                getBoolean(jsonObject, "water_tk_lv"),
                OvenCleaningStatus.values()[getInt(jsonObject, "clean_sta")],
                OvenCookingMode.values()[getInt(jsonObject, "coking_mode")],
                OvenRunState.values()[getInt(jsonObject, "run_sta")],
                OvenMode.values()[getInt(jsonObject, "mu_seqno")],
                getInt(jsonObject, "mu_temp"),
                getInt(jsonObject, "mu_steam_gr"),
                getInt(jsonObject, "mu_heat_tm"),
                getInt(jsonObject, "coking_rem_tm"),
                getBoolean(jsonObject, "steam_lv"),
                getBoolean(jsonObject, "cld_lock"),
                OvenErrorEnum.values()[getInt(jsonObject, "err_code")]
        );
    }

    public OvenState(
            boolean powerOn,
            OvenDoorState doorState,
            boolean waterTankInstalled,
            boolean waterTankFilled,
            OvenCleaningStatus cleaningStatus,
            OvenCookingMode cookingMode,
            OvenRunState runState,
            OvenMode mode,
            int temperature,
            int steamTemperature,
            int duration,
            int durationRemaining,
            boolean steamAssist,
            boolean childLock,
            OvenErrorEnum errorCode
    ) {
        this.powerOn = powerOn;
        this.doorState = doorState;
        this.waterTankInstalled = waterTankInstalled;
        this.waterTankFilled = waterTankFilled;
        this.cleaningStatus = cleaningStatus;
        this.cookingMode = cookingMode;
        this.runState = runState;
        this.mode = mode;
        this.temperature = temperature;
        this.steamTemperature = steamTemperature;
        this.duration = duration;
        this.durationRemaining = durationRemaining;
        this.steamAssist = steamAssist;
        this.childLock = childLock;
        this.errorCode = errorCode;
    }

    private static int getInt(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsInt() : 0;
    }

    private static boolean getBoolean(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }
}
