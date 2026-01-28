package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.adapters.knx.entities.sensor.MotionSensor;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;

public class KNXEntities {
    public static void registerAll(KNXAdapter adapter) throws Exception {
        adapter.register(new OccupancySensor(
                adapter, "bathroom", "sensor.presence",
                5, 1, 7
        ));

        adapter.register(new MotionSensor(
                adapter, "bathroom", "sensor.motion",
                5, 1, 4
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.vanity",
                5, 2, 1
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.main",
                5, 3, 1
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.shower",
                5, 4, 1
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.strip",
                5, 5, 1
        ));
    }
}
