package es.buni.hcb.config;

import es.buni.hcb.adapters.broadlink.BroadlinkAdapter;
import es.buni.hcb.adapters.broadlink.oven.BSHOven;

public class BroadlinkOvenConfig {
    public static void registerOven(BroadlinkAdapter adapter) {
        adapter.register(new BSHOven(
                adapter, "kitchen", "oven",
                "10.2.2.4", "34:8E:89:55:10:12"
        ));
    }
}
