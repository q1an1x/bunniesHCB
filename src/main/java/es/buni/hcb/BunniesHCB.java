package es.buni.hcb;

import es.buni.hcb.adapters.knx.KNXAdapter;
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
        Logger.info("Starting bunniesHomeControlBus");

        NetworkContext networkContext;
        try {
            InetAddress localAddress = null;
            for (int i = 0; i < args.length; i++) {
                if ("--local-address".equals(args[i]) && i + 1 < args.length) {
                    localAddress = InetAddress.getByName(args[i + 1]);
                    Logger.info("Using specified local address: " + localAddress.getHostAddress());
                }
            }
            if (localAddress == null) {
                localAddress = NetworkUtils.getFirstUsableIPv4();
                Logger.info("Using detected local address: " + localAddress.getHostAddress());
            }
            networkContext = new NetworkContext(localAddress);
        } catch (Exception e) {
            Logger.critical("Failed to determine local network address", e);
            return;
        }

        EntityRegistry registry = new EntityRegistry();
        AdapterManager adapterManager = new AdapterManager();
        ExternalInterfaceManager externalInterfaceManager = new ExternalInterfaceManager();

        adapterManager.register(new KNXAdapter(registry, networkContext));

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
    }
}
