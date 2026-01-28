package es.buni.hcb.core.external;

import es.buni.hcb.utils.Logger;
import java.util.ArrayList;
import java.util.List;

public class ExternalInterfaceManager {
    private final List<ExternalInterface> interfaces = new ArrayList<>();

    public void register(ExternalInterface iface) {
        interfaces.add(iface);
    }

    public void startAll() {
        for (ExternalInterface iface : interfaces) {
            try {
                Logger.info("Starting interface: " + iface.getName());
                iface.start();
            } catch (Exception e) {
                Logger.error("Failed to start interface " + iface.getName(), e);
            }
        }
    }

    public void stopAll() {
        for (ExternalInterface iface : interfaces) {
            try {
                Logger.info("Stopping interface: " + iface.getName());
                iface.stop();
            } catch (Exception e) {
                Logger.error("Error stopping interface " + iface.getName(), e);
            }
        }
    }
}
