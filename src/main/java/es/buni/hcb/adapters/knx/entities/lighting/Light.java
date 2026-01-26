package es.buni.hcb.adapters.knx.entities.lighting;

import es.buni.hcb.adapters.knx.KNXAdapter;

public class Light extends Switch {
    public static Light fromConvention(KNXAdapter adapter, String location, String id,
                                       int switchMainGroup, int switchMiddleGroup, int switchSubGroup)
            throws Exception {
        return new Light(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                switchMainGroup, switchMiddleGroup, switchSubGroup + 1
        );
    }

    public Light(KNXAdapter adapter, String location, String id,
                    int switchMainGroup, int switchMiddleGroup, int switchSubGroup,
                    int statusSwitchMainGroup, int statusSwitchMiddleGroup, int statusSwitchSubGroup)
            throws Exception {
        super(adapter, location, id,
                switchMainGroup, switchMiddleGroup, switchSubGroup,
                statusSwitchMainGroup, statusSwitchMiddleGroup, statusSwitchSubGroup);
    }
}
