package es.buni.hcb.adapters.knx.entities;

import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor extends KNXEntity {

    private final Set<GroupAddress> heartbeatAddresses;

    private final long heartbeatInterval;

    private volatile long lastSeen = System.currentTimeMillis();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    @Override
    public Set<GroupAddress> groupAddresses() {
        return heartbeatAddresses;
    }

    public HealthMonitor(KNXAdapter adapter, Set<GroupAddress> heartbeatAddresses) {
        this(adapter, "health-monitor", heartbeatAddresses, 15000);
    }

    public HealthMonitor(
            KNXAdapter adapter,
            String id,
            Set<GroupAddress> heartbeatAddresses,
            long heartbeatInterval
    ) {
        super(adapter, "system", id);
        this.heartbeatAddresses = heartbeatAddresses;
        this.heartbeatInterval = heartbeatInterval;
    }

    @Override
    protected boolean updateState(GroupAddress address, ProcessEvent event) throws Exception {
        lastSeen = System.currentTimeMillis();
        return false;
    }

    @Override
    protected void onStateUpdated(GroupAddress address, ProcessEvent event) {
    }

    @Override
    public void initialize() throws Exception {
        scheduler.scheduleAtFixedRate(
                this::checkHealth,
                heartbeatInterval,
                heartbeatInterval,
                TimeUnit.MILLISECONDS
        );

        super.initialize();
    }

    private void checkHealth() {
        long now = System.currentTimeMillis();
        if (now - lastSeen > ( heartbeatInterval * 3 )) {
            adapter.reconnect(
                    "KNX heartbeat timeout"
            );
        } else if (now - lastSeen > ( heartbeatInterval * 2 )) {
            Logger.warn("KNX heartbeat about to timeout");
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
