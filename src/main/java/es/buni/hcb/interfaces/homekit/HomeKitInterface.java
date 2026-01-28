package es.buni.hcb.interfaces.homekit;

import es.buni.hcb.core.Entity;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.core.external.ExternalInterface;
import es.buni.hcb.core.NetworkContext;
import es.buni.hcb.utils.Logger;
import es.buni.hcb.utils.Utils;
import io.github.hapjava.server.impl.HomekitRoot;
import io.github.hapjava.server.impl.HomekitServer;

import java.io.File;
import java.net.InetAddress;

public class HomeKitInterface extends ExternalInterface {

    public static String HOMEKIT_ENTITY_MANUFACTURER = "buni.es / 千";
    public static String HOMEKIT_BRIDGE_LABEL = "bunniesHCB";
    public static String HOMEKIT_BRIDGE_MODEL = "bunniesHomeControlBus";

    private final InetAddress local;
    private final int port;
    private final File authFile;

    private PersistentAuthInfo authInfo;
    private HomekitServer server;
    private HomekitRoot bridge;

    public HomeKitInterface(EntityRegistry registry, NetworkContext networkContext, File authFile, int port) {
        super(registry);
        this.local = networkContext.getLocalAddress();
        this.authFile = authFile;
        this.port = port;
    }

    @Override
    public void start() throws Exception {
        this.server = new HomekitServer(local, port);
        authInfo = new PersistentAuthInfo(authFile);

        bridge = server.createBridge(
                authInfo,
                HOMEKIT_BRIDGE_LABEL,
                port,
                HOMEKIT_ENTITY_MANUFACTURER,
                HOMEKIT_BRIDGE_MODEL,
                HOMEKIT_BRIDGE_LABEL,
                Utils.BUILD_DATE,
                Utils.BUILD_DATE
        );

        for (Entity entity : registry.getAllEntities()) {
            if (entity.isHomeKitAccessory()) {
                Logger.info("HomeKit: Adding " + entity.getType() + " " + entity.getNamedId());
                bridge.addAccessory(entity);
            }
        }

        bridge.start();
        Logger.info("HomeKit Bridge started on " + local.getHostAddress() + ":" + port + ". Pin: " + authInfo.getPin());
    }

    @Override
    public void stop() throws Exception {
        if (bridge != null) {
            bridge.stop();
        }
    }
}
