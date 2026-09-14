package es.buni.hcb.config.knx;

public enum AutomationType {
    AUTO("autolighting"),
    CONSTANT("constantlighting"),
    NIGHT("nightlighting"),
    ADAPTIVE("adaptivelighting");

    private final String keySuffix;

    AutomationType(String keySuffix) {
        this.keySuffix = keySuffix;
    }

    public String getKeySuffix() {
        return keySuffix;
    }
}