package es.buni.hcb.automation.modes;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.*;
import es.buni.hcb.automation.PolicyKind;
import es.buni.hcb.config.knx.ScenesEnum;
import java.util.*;
import static es.buni.hcb.automation.modes.ModeAction.Kind.*;

/** Household behavior lives here; the coordinator owns execution and restoration only. */
public final class ModeCatalog {
    private final KNXAdapter adapter;
    public ModeCatalog(KNXAdapter adapter) { this.adapter = adapter; }
    public Set<String> affectedRooms(HouseMode mode) {
        var rooms = new TreeSet<String>();
        if (mode == HouseMode.HOME) return rooms;
        for (var entity : adapter.entities()) {
            if (!(entity instanceof Light)) continue;
            String room = entity.getLocation();
            if (mode == HouseMode.MOVIE && !room.equals("livingroom")) continue;
            if (mode == HouseMode.GUEST && room.startsWith("bedroom.")) continue;
            rooms.add(room);
        }
        return rooms;
    }

    public boolean permits(HouseMode mode, String room, PolicyKind kind) {
        return switch (mode) {
            case HOME -> true;
            case AWAY, CLEANING -> false;
            case SLEEP -> kind == PolicyKind.NIGHT_LIGHT;
            case MOVIE -> !room.equals("livingroom");
            case GUEST -> room.startsWith("bedroom.");
        };
    }

    public Map<String, Boolean> automationSettings(HouseMode mode) {
        var desired = new LinkedHashMap<String, Boolean>();
        adapter.entities().stream().filter(Toggle.class::isInstance).map(Toggle.class::cast)
                .filter(t -> t.getIId().startsWith("toggle.")).sorted(Comparator.comparing(Toggle::getNamedId)).forEach(toggle -> {
                    String room = toggle.getLocation();
                    if (mode == HouseMode.HOME || (mode == HouseMode.MOVIE && !room.equals("livingroom"))
                            || (mode == HouseMode.GUEST && room.startsWith("bedroom."))) return;
                    desired.put(toggle.getNamedId(), mode == HouseMode.SLEEP && toggle.getIId().equals("toggle.nightlighting"));
                });
        return desired;
    }

    public List<ModeAction> lightingActions(HouseMode mode) {
        var actions = new ArrayList<ModeAction>();
        if (mode == HouseMode.MOVIE) actions.add(scene(ScenesEnum.LIVINGROOM_MOVIE));
        if (mode == HouseMode.GUEST) actions.add(scene(ScenesEnum.LIVINGROOM_HOME));
        if (mode == HouseMode.SLEEP) actions.add(scene(ScenesEnum.LIVINGROOM_NIGHT));
        var lights = adapter.entities().stream().filter(Light.class::isInstance).map(Light.class::cast)
                .sorted(Comparator.comparing(Light::getNamedId)).toList();
        for (var light : lights) {
            if (mode == HouseMode.AWAY || (mode == HouseMode.SLEEP && Set.of("entry", "corridor", "kitchen").contains(light.getLocation())))
                actions.add(new ModeAction(POWER, light.getNamedId(), 0));
            if (mode == HouseMode.CLEANING) {
                if (light instanceof Tunable) actions.add(new ModeAction(COLOR_TEMPERATURE, light.getNamedId(), 4000));
                actions.add(new ModeAction(POWER, light.getNamedId(), 1));
                if (light instanceof Dimmable) actions.add(new ModeAction(BRIGHTNESS, light.getNamedId(), 90));
            }
        }
        return List.copyOf(actions);
    }
    private static ModeAction scene(ScenesEnum scene) { return new ModeAction(SCENE, scene.name(), scene.getSceneNumber()); }
}
