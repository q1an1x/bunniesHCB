package es.buni.hcb.config;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.Curtain;
import es.buni.hcb.adapters.knx.entities.SceneController;
import es.buni.hcb.adapters.knx.entities.Toggle;
import es.buni.hcb.adapters.knx.entities.lighting.Dimmable;
import es.buni.hcb.adapters.knx.entities.lighting.Tunable;
import es.buni.hcb.adapters.knx.entities.sensor.IlluminanceSensor;
import es.buni.hcb.adapters.knx.entities.sensor.MotionSensor;
import es.buni.hcb.adapters.knx.entities.sensor.OccupancySensor;
import es.buni.hcb.automation.AdaptiveLightingPolicy;
import es.buni.hcb.automation.AutoLightingPolicy;
import es.buni.hcb.automation.ConstantLightingPolicy;
import es.buni.hcb.automation.NightLightingPolicy;
import es.buni.hcb.config.knx.*;
import io.calimero.GroupAddress;

public class KNXEntities {
    public final static GroupAddress SCENE_RECALL_GROUP_ADDRESS = new GroupAddress(0, 0, 2);

    public static void registerAll(KNXAdapter adapter) throws Exception {
        adapter.register(Tunable.fromConvention(
                adapter, "entry", "light.porch",
                2, 7, 1
        ));

        // --- Bathroom ---
        OccupancySensor bathroomOccupancySensor = new OccupancySensor(
                adapter, "bathroom", "sensor.presence",
                5, 1, 7
        );
        adapter.register(bathroomOccupancySensor);

        adapter.register(new MotionSensor(
                adapter, "bathroom", "sensor.motion",
                5, 1, 4
        ));

        IlluminanceSensor bathroomIlluminanceSensor = new IlluminanceSensor(
                adapter, "bathroom", "sensor.illuminance",
                5, 1, 2
        );
        adapter.register(bathroomIlluminanceSensor);

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.vanity",
                5, 2, 1
        ));

        Tunable bathroomMainLight = Tunable.fromConvention(
                adapter, "bathroom", "light.main",
                5, 3, 1
        );
        adapter.register(bathroomMainLight);

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.shower",
                5, 4, 1
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "bathroom", "light.strip",
                5, 5, 1
        ));


        Toggle bathroomAutoToggle = new Toggle(
                adapter, "bathroom", "toggle.autolighting",
                5, 0, 1
        );
        Toggle bathroomAdaptiveToggle = new Toggle(
                adapter, "bathroom", "toggle.adaptivelighting",
                5, 0, 2
        );
        Toggle bathroomNightToggle = new Toggle(
                adapter, "bathroom", "toggle.nightlighting",
                5, 0, 3
        );

        adapter.register(bathroomAutoToggle);
        adapter.register(bathroomAdaptiveToggle);
        adapter.register(bathroomNightToggle);

        (new AutoLightingPolicy(
                "automation.bathroom.autolighting", adapter,
                ScenesEnum.BATHROOM_MOTION.getSceneNumber(),
                bathroomAutoToggle,
                bathroomOccupancySensor,
                SCENE_RECALL_GROUP_ADDRESS,
                new GroupAddress(5, 0 , 31)
        )).start();
        (new NightLightingPolicy(
                "automation.bathroom.nightlighting", adapter,
                bathroomNightToggle,
                bathroomOccupancySensor,
                SCENE_RECALL_GROUP_ADDRESS,
                ScenesEnum.BATHROOM_NIGHT.getSceneNumber(),
                new GroupAddress(5, 0, 31)
        )).start();
        (new AdaptiveLightingPolicy(
                "automation.bedroom.north.adaptivelighting", adapter,
                bathroomAdaptiveToggle,
                5, 0, 22
        )).start();

        // --- Corridor
        adapter.register(new Toggle(
                adapter, "livingroom", "toggle.constantlighting",
                2, 0, 1
        ));
        Toggle livingRoomAdaptiveToggle = new Toggle(
                adapter, "livingroom", "toggle.adaptivelighting",
                2, 0, 2
        );
        adapter.register(livingRoomAdaptiveToggle);
        adapter.register(new FullNightModeButton(
                adapter, "corridor", "button.fullnightmode"
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "corridor", "light",
                6, 1, 1
        ));

        // --- Living room
        adapter.register(new StartVacuumButton(adapter));

        adapter.register(new OccupancySensor(
                adapter, "livingroom", "sensor.presence",
                2, 2, 6
        ));

        adapter.register(new MotionSensor(
                adapter, "livingroom", "sensor.motion",
                2, 2, 5
        ));

        adapter.register(new IlluminanceSensor(
                adapter, "livingroom", "sensor.illuminance",
                2, 2, 3,
                3
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.balcony",
                2, 1, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.strip",
                2, 3, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.televison",
                2, 4, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.couch",
                2, 4, 11
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.main",
                2, 4, 21
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.piano",
                2, 4, 41
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.floorlamp",
                2, 4, 51
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.televison.under",
                2, 5, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "livingroom", "light.balcony.cabnet",
                2, 6, 1
        ));

        adapter.register(Curtain.fromConvention(
                adapter, "livingroom", "curtain",
                2, 4, 101
        ));
        adapter.register(Curtain.fromConvention(
                adapter, "livingroom", "curtain.shade",
                2, 4, 111
        ));

        Toggle livingroomNightToggle = new Toggle(
                adapter, "livingroom", "toggle.nightlighting",
                2, 0, 3
        );
        adapter.register(livingroomNightToggle);

        (new AdaptiveLightingPolicy(
                "automation.livingroom.adaptivelighting", adapter,
                livingRoomAdaptiveToggle,
                2, 0, 31
        )).start();

        // --- Kitchen
        adapter.register(new OccupancySensor(
                adapter, "kitchen", "sensor.presence",
                1, 4, 1
        ));

        adapter.register(new IlluminanceSensor(
                adapter, "kitchen", "sensor.illuminance",
                1, 4, 2
        ));

        adapter.register(Tunable.fromConvention(
                adapter, "kitchen", "light.main",
                1, 1, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "kitchen", "light.displaycabinet",
                1, 2, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "kitchen", "light.sink",
                1, 3, 1
        ));
        adapter.register(Dimmable.fromConvention(
                adapter, "kitchen", "light.dining",
                2, 4, 31
        ));

        // --- Bedroom (North)
        OccupancySensor bedroomNorthOccupancySensor = new OccupancySensor(
                adapter, "bedroom.north", "sensor.presence",
                3, 1, 6
        );
        adapter.register(bedroomNorthOccupancySensor);
        adapter.register(new MotionSensor(
                adapter, "bedroom.north", "sensor.motion",
                3, 1, 5
        ));
        IlluminanceSensor bedroomNorthIlluminanceSensor = new IlluminanceSensor(
                adapter, "bedroom.north", "sensor.illuminance",
                3, 1, 3,
                20
        );
        adapter.register(bedroomNorthIlluminanceSensor);

        adapter.register(Curtain.fromConvention(
                adapter, "bedroom.north", "curtain",
                3, 1, 11
        ));

        adapter.register(Dimmable.fromConvention(
                adapter, "bedroom.north", "light.desklamp",
                3, 5, 1
        ));

        Tunable bedroomNorthMainLight = Tunable.fromConvention(
                adapter, "bedroom.north", "light.main",
                3, 6, 1
        );
        adapter.register(bedroomNorthMainLight);
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.north", "light.cabinet",
                3, 4, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.north", "light.desk",
                3, 7, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.north", "light.underbed",
                3, 3, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.north", "light.strip",
                3, 2, 1
        ));

        Toggle bedroomNorthConstantToggle = new Toggle(
                adapter, "bedroom.north", "toggle.constantlighting",
                3, 0, 1
        );
        Toggle bedroomNorthAdaptiveToggle = new Toggle(
                adapter, "bedroom.north", "toggle.adaptivelighting",
                3, 0, 2
        );
        Toggle bedroomNorthNightToggle = new Toggle(
                adapter, "bedroom.north", "toggle.nightlighting",
                3, 0, 3
        );
        adapter.register(bedroomNorthConstantToggle);
        adapter.register(bedroomNorthAdaptiveToggle);
        adapter.register(bedroomNorthNightToggle);
        adapter.register(new DelayedOffButton(
                adapter, "bedroom.north", "delayedoff",
                3, 0, 5,
                new GroupAddress(3, 0, 31)
        ));


        (new ConstantLightingPolicy(
                "automation.bedroom.north.constantlighting", adapter,
                bedroomNorthConstantToggle,
                bedroomNorthNightToggle,
                bedroomNorthIlluminanceSensor,
                bedroomNorthOccupancySensor,
                new GroupAddress(3, 0 , 21),
                new GroupAddress(3, 0, 31),
                bedroomNorthMainLight,
                100,
                1
        )).start();
        (new NightLightingPolicy(
                "automation.bedroom.north.nightlighting", adapter,
                bedroomNorthNightToggle,
                bedroomNorthOccupancySensor,
                SCENE_RECALL_GROUP_ADDRESS,
                ScenesEnum.BEDROOM_NORTH_NIGHT.getSceneNumber(),
                new GroupAddress(3, 0, 31)
        )).start();
        (new AdaptiveLightingPolicy(
                "automation.bedroom.north.adaptivelighting", adapter,
                bedroomNorthAdaptiveToggle,
                3, 0, 22
        )).start();

        // --- Bedroom (South)
        OccupancySensor bedroomSouthOccupancySensor = new OccupancySensor(
                adapter, "bedroom.south", "sensor.presence",
                4, 1, 5
        );
        adapter.register(bedroomSouthOccupancySensor);
        adapter.register(new MotionSensor(
                adapter, "bedroom.south", "sensor.motion",
                4, 1, 4
        ));
        IlluminanceSensor bedroomSouthIlluminanceSensor = new IlluminanceSensor(
                adapter, "bedroom.south", "sensor.illuminance",
                4, 1, 2
        );
        adapter.register(bedroomSouthIlluminanceSensor);

        adapter.register(Curtain.fromConvention(
                adapter, "bedroom.south", "curtain",
                4, 6, 1
        ));

        Tunable bedroomSouthMainLight = Tunable.fromConvention(
                adapter, "bedroom.south", "light.main",
                4, 4, 1
        );
        adapter.register(bedroomSouthMainLight);
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.south", "light.bedside.lamp",
                4, 5, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.south", "light.bedside.strip",
                4, 3, 1
        ));
        adapter.register(Tunable.fromConvention(
                adapter, "bedroom.south", "light.strip",
                4, 2, 1
        ));

        Toggle bedroomSouthConstantToggle = new Toggle(
                adapter, "bedroom.south", "toggle.constantlighting",
                4, 0, 1
        );
        Toggle bedroomSouthAdaptiveToggle = new Toggle(
                adapter, "bedroom.south", "toggle.adaptivelighting",
                4, 0, 2
        );
        Toggle bedroomSouthNightToggle = new Toggle(
                adapter, "bedroom.south", "toggle.nightlighting",
                4, 0, 3
        );
        adapter.register(bedroomSouthConstantToggle);
        adapter.register(bedroomSouthAdaptiveToggle);
        adapter.register(bedroomSouthNightToggle);
        adapter.register(new DelayedOffButton(
                adapter, "bedroom.south", "delayedoff",
                4, 0, 5,
                new GroupAddress(4, 0, 31)
        ));

        adapter.register(new TVPowerButton(adapter));
        adapter.register(new TVPlayPauseButton(adapter));

        (new ConstantLightingPolicy(
                "automation.bedroom.south.constantlighting", adapter,
                bedroomSouthConstantToggle,
                bedroomSouthNightToggle,
                bedroomSouthIlluminanceSensor,
                bedroomSouthOccupancySensor,
                new GroupAddress(4, 0 , 21),
                new GroupAddress(4, 0, 31),
                bedroomSouthMainLight,
                100,
                1
        )).start();
        (new NightLightingPolicy(
                "automation.bedroom.south.nightlighting", adapter,
                bedroomSouthNightToggle,
                bedroomSouthOccupancySensor,
                SCENE_RECALL_GROUP_ADDRESS,
                ScenesEnum.BEDROOM_SOUTH_NIGHT.getSceneNumber(),
                new GroupAddress(4, 0, 31)
        )).start();
        (new AdaptiveLightingPolicy(
                "automation.bedroom.south.adaptivelighting", adapter,
                bedroomSouthAdaptiveToggle,
                4, 0, 22
        )).start();


        // --- Automation
        adapter.register(new SceneController(adapter, 0, 0, 2));
        (new SceneAutomationManager(adapter)).start();
    }
}
