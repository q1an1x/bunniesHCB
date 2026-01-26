package es.buni.hcb.core;

import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class AdapterManager {

    private final List<Adapter> adapters = new ArrayList<>();

    public void register(Adapter adapter) {
        adapters.add(adapter);
    }

    public void startAll() {
        for (Adapter adapter : adapters) {
            try {
                Logger.info("Starting adapter: " + adapter.getName());
                adapter.start();
            } catch (Exception e) {
                Logger.error("Failed to start adapter: " + adapter.getName(), e);

                shutdown();
                throw new RuntimeException("Adapter startup failed", e);
            }
        }
    }

    public void shutdown() {
        for (Adapter adapter : adapters) {
            try {
                adapter.stop();
            } catch (Exception e) {
                Logger.error("Error stopping adapter: " + adapter.getName(), e);
            }
        }
    }
}
