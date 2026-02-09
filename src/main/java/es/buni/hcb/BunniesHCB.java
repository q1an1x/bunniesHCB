package es.buni.hcb;

import es.buni.hcb.adapters.broadlink.BroadlinkAdapter;
import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.config.BroadlinkOvenConfig;
import es.buni.hcb.config.HomeAssistantEntities;
import es.buni.hcb.core.AdapterManager;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.core.NetworkContext;
import es.buni.hcb.interfaces.homekit.HomeKitInterface;
import es.buni.hcb.utils.*;

import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;

import es.buni.hcb.core.external.ExternalInterfaceManager;

public class BunniesHCB {
    private static final int HOMEKIT_BRIDGE_PORT = 51826;

    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        boolean enableOvenHomeKit = false;
        for (String arg : args) {
            if ("--debug".equals(arg)) {
                Debug.ENABLED = true;
                break;
            } else if ("--oven-homekit".equals(arg)) {
                enableOvenHomeKit = true;
            }
        }

        if (! Debug.ENABLED) {
            LoggingBootstrap.configure(false);
        }

        Logger.info("Starting bunniesHomeControlBus build " + Utils.BUILD_DATE);

        InetAddress localAddress = null;
        String haHost = null;
        String haToken = null;

        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                String value = (i + 1 < args.length) ? args[i + 1] : null;

                if ("--local-address".equals(arg) && value != null) {
                    localAddress = InetAddress.getByName(value);
                    Logger.info("Using specified local address: " + localAddress.getHostAddress());
                } else if ("--ha-host".equals(arg) && value != null) {
                    haHost = value;
                } else if ("--ha-token".equals(arg) && value != null) {
                    haToken = value;
                }
            }

            if (localAddress == null) {
                localAddress = NetworkUtils.getFirstUsableIPv4();
                Logger.info("Using detected local address: " + localAddress.getHostAddress());
            }

            NetworkContext networkContext = new NetworkContext(localAddress);

            EntityRegistry registry = new EntityRegistry();
            AdapterManager adapterManager = new AdapterManager();
            ExternalInterfaceManager externalInterfaceManager = new ExternalInterfaceManager();

            adapterManager.register(new KNXAdapter(registry, networkContext));

            BroadlinkAdapter broadlinkAdapter = new BroadlinkAdapter(registry);
            adapterManager.register(broadlinkAdapter);
            BroadlinkOvenConfig.registerOven(broadlinkAdapter, enableOvenHomeKit);

            if (haHost != null && haToken != null) {
                Logger.info("Initializing Home Assistant Adapter (" + haHost + ")");
                HomeAssistantAdapter haAdapter = new HomeAssistantAdapter(registry, haHost, haToken);
                adapterManager.register(haAdapter);

                HomeAssistantEntities.registerAll(haAdapter);
            } else {
                Logger.warn("Skipping Home Assistant Adapter: --ha-host and --ha-token are required");
            }

            externalInterfaceManager.register(
                    new HomeKitInterface(
                            registry,
                            networkContext,
                            new File("homekit-auth.bin"),
                            HOMEKIT_BRIDGE_PORT
                    )
            );

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Logger.info("Shutdown signal received");
                try {
                    externalInterfaceManager.stopAll();
                    adapterManager.shutdown();
                } catch (Exception e) {
                    Logger.error("Error during adapter shutdown", e);
                } finally {
                    shutdownLatch.countDown();
                }
            }));

            try {
                adapterManager.startAll();
                externalInterfaceManager.startAll();
            } catch (Exception e) {
                Logger.critical("Fatal startup error", e);
                return;
            }

            Logger.info("Started bunniesHomeControlBus");

            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                Logger.warn("Shutdown latch interrupted");
                Thread.currentThread().interrupt();
            }

            Logger.info("bunniesHomeControlBus exiting");

        } catch (Exception e) {
            Logger.critical("Failed to initialize system", e);
        }
    }
}
