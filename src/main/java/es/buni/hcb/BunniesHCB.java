package es.buni.hcb;

import com.google.gson.GsonBuilder;
import es.buni.hcb.adapters.broadlink.BroadlinkAdapter;
import es.buni.hcb.adapters.broadlink.oven.BSHOven;
import es.buni.hcb.adapters.homeassistant.HomeAssistantAdapter;
import es.buni.hcb.adapters.knx.*;
import es.buni.hcb.config.*;
import es.buni.hcb.core.*;
import es.buni.hcb.core.external.ExternalInterfaceManager;
import es.buni.hcb.interfaces.homekit.*;
import es.buni.hcb.utils.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

public final class BunniesHCB {
    public static void main(String[] args) {
        int code = run(args);
        if (code != 0) System.exit(code);
    }

    static int run(String[] args) {
        try {
            RuntimeOptions options = RuntimeOptions.parse(args, System.getenv());
            if (options.help()) { help(); return 0; }
            Debug.ENABLED = options.debug();
            LoggingBootstrap.configure(options.debug());
            TimeZone.setDefault(TimeZone.getTimeZone(options.zone()));
            var registry = new EntityRegistry();
            var network = new NetworkContext(options.knx().mode() == KnxMode.OFFLINE ? InetAddress.getLoopbackAddress()
                    : options.localAddress() == null ? NetworkUtils.getFirstUsableIPv4() : InetAddress.getByName(options.localAddress()));
            var knx = new KNXAdapter(registry, options.knx(), new CalimeroConnectionFactory(network, options.knx()), Clock.system(options.zone()));
            knx.configure();
            if (options.knx().mode() == KnxMode.OFFLINE) {
                try {
                    if (options.inventory() != null) writeInventory(knx, options.inventory());
                    Logger.info("Offline validation: " + knx.entities().size() + " KNX entities, " + knx.bindings().size()
                            + " bindings; no network or device operations");
                } finally { knx.stop(); registry.getEventBus().close(); }
                return 0;
            }
            var adapters = new AdapterManager();
            var interfaces = new ExternalInterfaceManager();
            adapters.register(knx);
            if (options.knx().mode() == KnxMode.LIVE) {
                String token = options.resolvedHaToken();
                if (options.haHost() != null) {
                    var ha = new HomeAssistantAdapter(registry, options.haHost(), token);
                    HomeAssistantEntities.registerAll(ha);
                    adapters.register(ha);
                }
                if (options.oven()) {
                    var broadlink = new BroadlinkAdapter(registry);
                    broadlink.register(new BSHOven(broadlink, "kitchen", "oven", options.ovenHost(), options.ovenMac(), options.ovenHomekit()));
                    adapters.register(broadlink);
                }
                if (options.homekit()) {
                    ConfiguredNameStore.configureDefault(options.stateDir().resolve("configured-names.properties"));
                    interfaces.register(new HomeKitInterface(registry, network, options.stateDir().resolve("homekit-auth.bin").toFile(), 51826));
                }
            }
            Files.createDirectories(options.stateDir());
            // Do not allow a second controller to use the same state and pairing identity.
            try (var lockChannel = java.nio.channels.FileChannel.open(options.stateDir().resolve("hcb.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                var lock = lockChannel.tryLock();
                if (lock == null) throw new IllegalStateException("Another HCB process owns this state directory");
                try (lock) {
                    var health = new HealthReporter(knx, options.stateDir().resolve("health.json"));
                    var stopped = new CountDownLatch(1);
                    var shutdownStarted = new java.util.concurrent.atomic.AtomicBoolean();
                    Runnable shutdown = () -> {
                        if (shutdownStarted.compareAndSet(false, true)) {
                            interfaces.stopAll(); adapters.shutdown(); registry.getEventBus().close(); health.close(); stopped.countDown();
                        }
                    };
                    var hook = new Thread(shutdown, "hcb-shutdown");
                    Runtime.getRuntime().addShutdownHook(hook);
                    try {
                        health.start();
                        adapters.startAll();
                        interfaces.startAll();
                        Logger.info("HCB started in " + options.knx().mode() + " mode");
                        if (options.observeSeconds() > 0) stopped.await(options.observeSeconds(), TimeUnit.SECONDS);
                        else stopped.await();
                        Logger.info("KNX received=" + knx.receivedTelegrams() + ", dropped=" + knx.droppedTelegrams());
                    } finally {
                        shutdown.run();
                        try { Runtime.getRuntime().removeShutdownHook(hook); } catch (IllegalStateException ignored) { }
                    }
                }
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 130;
        } catch (Exception e) {
            Logger.error("HCB failed: " + e.getMessage());
            return 1;
        }
    }

    private static void writeInventory(KNXAdapter knx, Path file) throws Exception {
        var entries = knx.bindings().stream().map(b -> Map.of("owner", b.owner(), "property", b.property(),
                "address", b.address().toString(), "dpt", b.dpt(), "role", b.role().name(),
                "readable", b.readable(), "writable", b.writable())).toList();
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Files.writeString(absolute, new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("schema", 1, "bindings", entries)));
    }

    private static void help() {
        System.out.println("""
                bunniesHCB — defaults to OFFLINE (no sockets or device I/O)
                --mode offline [--inventory PATH]
                --mode observe --knx-gateway HOST [--observe-seconds N]
                --mode live --knx-gateway HOST [--enable-automations] [--enable-time-service]
                --local-address IP --knx-port 3671 --knx-nat --knx-silence-seconds 45
                --state-dir PATH --timezone Asia/Shanghai --disable-homekit
                --ha-host HOST [--ha-token-file PATH] (or HCB_HA_TOKEN / HCB_HA_TOKEN_FILE)
                --enable-oven --oven-host HOST --oven-mac MAC [--oven-homekit]
                --debug --help
                Observe creates a tunnel but sends no group reads/writes and starts no integrations.
                Never point a second LIVE controller at the same installation.
                """);
    }
}
