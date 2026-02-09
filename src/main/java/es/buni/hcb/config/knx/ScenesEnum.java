package es.buni.hcb.config.knx;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public enum ScenesEnum {
    BATHROOM_MOTION(2 - 1, "bathroom"),
    BATHROOM_NIGHT(3 - 1, "bathroom"),
    BATHROOM_WASHBASIN(
            19 - 1, "bathroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    BATHROOM_SHOWER(
            20 - 1, "bathroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),

    LIVINGROOM_NIGHT(4 - 1, "livingroom"),
    LIVINGROOM_MOVIE(
            5 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    LIVINGROOM_BRIGHT(
            7 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    LIVINGROOM_HOME(
            8 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    LIVINGROOM_ECO(
            9 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    LIVINGROOM_NORMAL(
            10 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    LIVINGROOM_WARM(
            21 - 1, "livingroom",
            EnumSet.of(AutomationType.ADAPTIVE)
    ),
    LIVINGROOM_EFFICIENCY(
            22 - 1, "livingroom",
            EnumSet.of(AutomationType.ADAPTIVE)
    ),

    BEDROOM_NORTH_NIGHT(11 - 1, "bedroom.north"),
    BEDROOM_NORTH_BRIGHT(
            12 - 1, "bedroom.north",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT, AutomationType.ADAPTIVE)
    ),
    BEDROOM_NORTH_MAIN(
            13 - 1, "bedroom.north",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    BEDROOM_NORTH_WARM(
            14 - 1, "bedroom.north",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT, AutomationType.ADAPTIVE)
    ),

    BEDROOM_SOUTH_NIGHT(17 - 1, "bedroom.south"),
    BEDROOM_SOUTH_BRIGHT(
            15 - 1, "bedroom.south",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT, AutomationType.ADAPTIVE)
    ),
    BEDROOM_SOUTH_WARM(
            16 - 1, "bedroom.south",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT, AutomationType.ADAPTIVE)
    ),
    BEDROOM_SOUTH_MAIN(
            18 - 1, "bedroom.south",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),

    LIVINGROOM_ALL_OFF(
            23 - 1, "livingroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    BATHROOM_ALL_OFF(
            24 - 1, "bathroom",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    BEDROOM_NORTH_ALL_OFF(
            25 - 1, "bedroom.north",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    ),
    BEDROOM_SOUTH_ALL_OFF(
            26 - 1, "bedroom.south",
            EnumSet.of(AutomationType.CONSTANT, AutomationType.NIGHT)
    );

    private final int sceneNumber;
    private final String location;
    private final Set<AutomationType> disabledAutomations;

    ScenesEnum(int sceneNumber, String location) {
        this(sceneNumber, location, EnumSet.noneOf(AutomationType.class));
    }

    ScenesEnum(int sceneNumber, String location, Set<AutomationType> disabledAutomations) {
        this.sceneNumber = sceneNumber;
        this.location = location;
        this.disabledAutomations = disabledAutomations;
    }

    public int getSceneNumber() {
        return sceneNumber;
    }

    public String getLocation() {
        return location;
    }

    public Set<AutomationType> getDisabledAutomations() {
        return disabledAutomations;
    }

    public static ScenesEnum fromNumber(int number) {
        return Arrays.stream(values())
                .filter(s -> s.sceneNumber == number)
                .findFirst()
                .orElse(null);
    }
}
