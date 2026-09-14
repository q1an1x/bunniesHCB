package es.buni.hcb.utils;

import com.google.gson.Gson;
import es.buni.hcb.adapters.knx.KNXAdapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

/** Private local diagnostics, without opening another network service. */
public final class HealthReporter implements AutoCloseable {
    private final KNXAdapter adapter;
    private final Path file;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("hcb-health-file").factory());
    private boolean writeFailed;

    public HealthReporter(KNXAdapter adapter, Path file) {
        this.adapter = adapter; this.file = file;
    }
    public void start() { timer.scheduleWithFixedDelay(this::write, 0, 10, TimeUnit.SECONDS); }
    private synchronized void write() {
        var entities = adapter.entities().stream().filter(KNXEntity.class::isInstance).map(KNXEntity.class::cast).toList();
        var state = new java.util.LinkedHashMap<String, Object>(Map.of("updatedAt", Instant.now().toString(), "mode", adapter.settings().mode().name(),
                "knxState", adapter.connectionState().name(), "generation", adapter.generation(),
                "receivedTelegrams", adapter.receivedTelegrams(), "droppedTelegrams", adapter.droppedTelegrams(),
                "silenceSeconds", adapter.settings().silenceTimeout().toSeconds(),
                "entities", entities.size(), "entitiesWithKnownState", entities.stream().filter(KNXEntity::isStateKnown).count()));
        if (adapter.houseModes() != null) state.put("houseMode", adapter.houseModes().status());
        state.put("manualOverrides", adapter.manualOverrides().active());
        try {
            PrivateFiles.writeAtomically(file, gson.toJson(state).getBytes(StandardCharsets.UTF_8));
            writeFailed = false;
        } catch (Exception e) {
            if (!writeFailed) Logger.error("Cannot write private health report: " + e.getMessage());
            writeFailed = true;
        }
    }
    @Override public void close() { timer.shutdownNow(); write(); }
}
