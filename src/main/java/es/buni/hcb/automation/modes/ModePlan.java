package es.buni.hcb.automation.modes;

import java.util.List;

public record ModePlan(HouseMode mode, List<ModeAction> actions, List<String> warnings) {
    public ModePlan { actions = List.copyOf(actions); warnings = List.copyOf(warnings); }
}
