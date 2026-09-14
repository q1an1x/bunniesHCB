package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.lighting.Light;
import es.buni.hcb.automation.ManualOverrides;
import io.calimero.GroupAddress;
import java.util.List;
import java.util.Set;

/** Native panel commands confirmed against ETS; these are observations, never relayed writes. */
public final class KNXPanelInputs {
    private KNXPanelInputs() { }

    public static void registerAll(KNXAdapter adapter) {
        // The entrance's "whole living room" addresses also reach kitchen and entrance lights.
        relative(adapter, "2/0/11", false, Set.of("livingroom", "kitchen", "entry"));
        relative(adapter, "2/0/12", true, Set.of("livingroom", "kitchen", "entry"));
        relative(adapter, "2/0/21", false, Set.of("livingroom"));
        relative(adapter, "2/0/22", true, Set.of("livingroom"));
        for (var room : List.of("bedroom.north", "bedroom.south", "bathroom")) {
            int main = switch (room) { case "bedroom.north" -> 3; case "bedroom.south" -> 4; default -> 5; };
            relative(adapter, main + "/0/11", false, Set.of(room));
            relative(adapter, main + "/0/12", true, Set.of(room));
        }
        var allOff = new GroupAddress(0, 0, 1);
        adapter.declareEvent("panels", "lightingAllOff", allOff, "1.001");
        // This is a timed manual OFF, not evidence that all occupants have left home.
        adapter.entities().stream().filter(Light.class::isInstance).map(e -> e.getLocation()).distinct()
                .forEach(room -> adapter.manualOverrides().registerOff(room, allOff));
    }

    private static void relative(KNXAdapter adapter, String address, boolean color, Set<String> rooms) {
        final GroupAddress ga;
        try { ga = new GroupAddress(address); }
        catch (io.calimero.KNXFormatException impossible) { throw new IllegalArgumentException(impossible); }
        adapter.declareEvent("panels", (color ? "relativeColor." : "relativeBrightness.") + address, ga, "3.007");
        rooms.forEach(room -> adapter.manualOverrides().registerRelative(room, ga,
                color ? ManualOverrides.COLOR : ManualOverrides.LIGHT_LEVEL));
    }
}
