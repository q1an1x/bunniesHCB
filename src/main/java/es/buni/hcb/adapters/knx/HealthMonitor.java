package es.buni.hcb.adapters.knx;

import es.buni.hcb.utils.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {
    private final KNXAdapter adapter;

    private final long heartbeatInterval;
    private volatile long lastSeen = System.currentTimeMillis();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public HealthMonitor(KNXAdapter adapter) {
        this(adapter, 15000);
    }

    public HealthMonitor(KNXAdapter adapter, long heartbeatInterval) {
        this.adapter = adapter;
        this.heartbeatInterval = heartbeatInterval;

        scheduler.scheduleAtFixedRate(
                this::checkHealth,
                heartbeatInterval,
                heartbeatInterval,
                TimeUnit.MILLISECONDS
        );
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public void receivedTelegram() {
        lastSeen = System.currentTimeMillis();
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
}
