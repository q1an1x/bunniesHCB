package es.buni.hcb.adapters.broadlink.oven;

public enum OvenMode {

    NONE(
            null,
            null,
            null,
            null
    ),

    CONVECTION(
            OvenPresetType.TEMPERATURE,
            180,
            40,
            230
    ),

    CONVECTION_GRILL(
            OvenPresetType.TEMPERATURE,
            180,
            100,
            230
    ),

    GRILL(
            OvenPresetType.TEMPERATURE,
            170,
            170,
            170
    ),

    REHEAT(
            OvenPresetType.TEMPERATURE,
            120,
            120,
            120
    ),

    STEAMING(
            OvenPresetType.STEAM_GRILL,
            100,
            100,
            100
    ),

    ENHANCED_STEAMING(
            OvenPresetType.STEAM_GRILL,
            110,
            110,
            110
    ),

    FERMENTATION(
            OvenPresetType.STEAM_GRILL,
            40,
            40,
            40
    ),

    DEFROST(
            OvenPresetType.STEAM_GRILL,
            45,
            45,
            45
    );

    private final OvenPresetType presetType;
    private final Integer defaultTemperature;
    private final Integer minTemperature;
    private final Integer maxTemperature;

    OvenMode(OvenPresetType presetType, Integer defaultTemperature, Integer minTemperature, Integer maxTemperature) {
        this.presetType = presetType;
        this.defaultTemperature = defaultTemperature;
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
    }

    /** Keeps ordinal-based protocol mapping intact */
    public int getProtocolId() {
        return ordinal();
    }

    /** Returns the preset type or null if none */
    public OvenPresetType getPresetType() {
        return presetType;
    }

    public Integer getDefaultTemperature() {
        return defaultTemperature;
    }

    public Integer getMinTemperature() {
        return minTemperature;
    }

    public Integer getMaxTemperature() {
        return maxTemperature;
    }

    public boolean hasPreset() {
        return presetType != null;
    }

    public boolean isValueAllowed(int value) {
        if (!hasPreset()) return false;
        return value >= minTemperature && value <= maxTemperature;
    }
}
