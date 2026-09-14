package es.buni.hcb.automation.modes;

/** An inspectable intent, resolved against the typed entity catalog before any write. */
public record ModeAction(Kind kind, String target, int value) {
    public enum Kind { AUTOMATION, POWER, BRIGHTNESS, COLOR_TEMPERATURE, SCENE }
    public ModeAction {
        java.util.Objects.requireNonNull(kind);
        if (target == null || target.isBlank()) throw new IllegalArgumentException("Mode action needs a target");
        boolean valid = switch (kind) {
            case AUTOMATION, POWER -> value == 0 || value == 1;
            case BRIGHTNESS -> value >= 0 && value <= 100;
            case COLOR_TEMPERATURE -> value >= es.buni.hcb.adapters.knx.entities.lighting.Tunable.COLOR_TEMPERATURE_MIN_KELVIN
                    && value <= es.buni.hcb.adapters.knx.entities.lighting.Tunable.COLOR_TEMPERATURE_MAX_KELVIN;
            case SCENE -> value >= 0 && value <= 63;
        };
        if (!valid) throw new IllegalArgumentException("Mode action value outside supported range");
    }
}
