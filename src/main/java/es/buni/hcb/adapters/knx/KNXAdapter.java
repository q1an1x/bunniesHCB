package es.buni.hcb.adapters.knx;

import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.adapters.knx.entities.HealthMonitor;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.config.KNXEntities;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.core.NetworkContext;
import es.buni.hcb.utils.Logger;

import io.calimero.CloseEvent;
import io.calimero.DetachEvent;
import io.calimero.GroupAddress;
import io.calimero.knxnetip.KNXnetIPConnection;
import io.calimero.link.KNXNetworkLink;
import io.calimero.link.KNXNetworkLinkIP;
import io.calimero.link.NetworkLinkListener;
import io.calimero.link.medium.TPSettings;
import io.calimero.process.ProcessCommunicator;
import io.calimero.process.ProcessCommunicatorImpl;
import io.calimero.process.ProcessEvent;
import io.calimero.process.ProcessListener;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class KNXAdapter extends Adapter implements Reconnectable {

    private static final String KNX_GATEWAY_HOST = "knx-interface.lan.buni.es";
    private static final int KNX_GATEWAY_PORT = KNXnetIPConnection.DEFAULT_PORT;

    protected final NetworkContext networkContext;

    private KNXNetworkLink link;
    private ProcessCommunicator pc;

    enum ConnectionState {
        STOPPED,
        CONNECTED,
        RECONNECTING
    }

    private volatile ConnectionState state = ConnectionState.STOPPED;
    private final ScheduledExecutorService reconnectExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "reconnection")
            );
    private static final long INITIAL_BACKOFF = TimeUnit.MINUTES.toMillis(1);
    private static final long MAX_BACKOFF = TimeUnit.DAYS.toMillis(1);

    private long currentBackoff = INITIAL_BACKOFF;

    private HealthMonitor healthMonitor;
    private final Map<GroupAddress, Set<KNXEntity>> entitiesByGroupAddress = new ConcurrentHashMap<>();

    public KNXAdapter(EntityRegistry registry, NetworkContext networkContext) {
        super("knx", registry);
        this.networkContext = networkContext;
    }

    public void register(KNXEntity entity) {
        super.register(entity);

        for (GroupAddress ga : entity.groupAddresses()) {
            entitiesByGroupAddress
                    .computeIfAbsent(ga, k -> ConcurrentHashMap.newKeySet())
                    .add(entity);
        }
    }

    public void reconnect(String reason) {
        if (state == ConnectionState.CONNECTED) {
            Logger.warn("KNX unhealthy: " + reason);
            startReconnection(reason);
        }
    }

    public synchronized void startReconnection(String reason) {
        if (state == ConnectionState.RECONNECTING) {
            return;
        }

        state = ConnectionState.RECONNECTING;
        currentBackoff = INITIAL_BACKOFF;

        reconnectExecutor.execute(() -> {
            Logger.warn("Reconnecting to KNX gateway: " + reason);

            while (state == ConnectionState.RECONNECTING) {
                try {
                    stop();
                    Thread.sleep(2000);

                    start();
                    Logger.info("Reconnected to KNX gateway");
                    return;

                } catch (Exception e) {
                    Logger.warn("KNX reconnect failed, retrying in "
                            + (currentBackoff / 1000) + "s: "
                            + e.getMessage()
                    );
                    try {
                        Thread.sleep(currentBackoff);
                    } catch (InterruptedException ignored) {
                        return;
                    }

                    currentBackoff = Math.min(
                            currentBackoff * 2,
                            MAX_BACKOFF
                    );
                }
            }
        });
    }

    @Override
    public void start() throws Exception {
        Logger.info("Connecting to KNX IP Gateway");

        link = KNXNetworkLinkIP.newTunnelingLink(
                new InetSocketAddress(networkContext.getLocalAddress(), 0),
                new InetSocketAddress(KNX_GATEWAY_HOST, KNX_GATEWAY_PORT),
                false,
                new TPSettings()
        );

        link.addLinkListener(new NetworkLinkListener() {
            @Override
            public void linkClosed(CloseEvent e) {
                Logger.warn("KNX link closed: " + e.getReason());
                if (! e.getReason().equals("user request")) {
                    reconnect("link closed");
                }
            }
        });

        KNXEntities.registerAll(this);
        healthMonitor = new HealthMonitor(this);

        pc = new ProcessCommunicatorImpl(link);
        pc.addProcessListener(new Listener());

        super.start();

        state = ConnectionState.CONNECTED;
        Logger.info("KNX adapter started");
    }

    @Override
    public void stop() throws Exception {
        Logger.info("Stopping KNX adapter");

        if (pc != null) {
            pc.detach();
        }
        if (link != null) {
            link.close();
        }

        healthMonitor.shutdown();
        healthMonitor = null;
        entitiesByGroupAddress.clear();
        super.stop();
    }

    public ProcessCommunicator communicator() {
        return pc;
    }

    private class Listener implements ProcessListener {
        @Override
        public void groupReadRequest(ProcessEvent event) {
        }

        @Override
        public void groupReadResponse(ProcessEvent event) {
            handleEvent(event);
        }

        @Override
        public void groupWrite(ProcessEvent event) {
            handleEvent(event);
        }

        @Override
        public void detached(DetachEvent event) {
        }

        private void handleEvent(ProcessEvent event) {
            healthMonitor.receivedTelegram();

            GroupAddress address = event.getDestination();

            var entities = entitiesByGroupAddress.get(address);
            if (entities == null) {
                return;
            }

            for (KNXEntity entity : entities) {
                try {
                    entity.handleBusUpdate(address, event);
                } catch (Exception ex) {
                    Logger.error(
                            "Error handling KNX update for " + entity.getNamedId() + " (" + address + ")",
                            ex
                    );
                }
            }
        }
    }
}
