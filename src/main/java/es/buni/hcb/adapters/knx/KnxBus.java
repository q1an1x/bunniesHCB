package es.buni.hcb.adapters.knx;

import io.calimero.GroupAddress;
import io.calimero.datapoint.Datapoint;
import io.calimero.dptxlator.DPTXlator;
import io.calimero.process.ProcessCommunicator;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** The only application path to group reads/writes. Never queues commands across sessions. */
public final class KnxBus {
    private final KNXAdapter adapter;
    private final ReentrantLock lock = new ReentrantLock(true);
    private long lastSendNanos;

    KnxBus(KNXAdapter adapter) { this.adapter = adapter; }

    @FunctionalInterface private interface Operation<T> { T run(ProcessCommunicator pc) throws Exception; }

    private <T> T execute(boolean write, GroupAddress address, Operation<T> operation) throws Exception {
        Objects.requireNonNull(address);
        if (adapter.settings().mode() != KnxMode.LIVE) {
            throw new IllegalStateException("Group I/O is disabled in " + adapter.settings().mode() + " mode");
        }
        KnxConnection session = adapter.connection();
        long generation = adapter.generation();
        if (adapter.processingGeneration() != generation) {
            throw new IllegalStateException("Event belongs to a previous KNX connection");
        }
        if (session == null || !session.isOpen() || (write && !adapter.isReady())) {
            throw new IllegalStateException("KNX is not ready; command was not queued");
        }
        if (write && !adapter.isWriteAllowed(address)) {
            throw new IllegalArgumentException("Undeclared KNX command address: " + address);
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        if (!lock.tryLock(2, TimeUnit.SECONDS)) throw new IllegalStateException("KNX sender busy; retry explicitly");
        try {
            long remaining = adapter.settings().minSendInterval().toNanos() - (System.nanoTime() - lastSendNanos);
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
            if (System.nanoTime() > deadline || generation != adapter.generation()
                    || session != adapter.connection() || !session.isOpen()
                    || (write && !adapter.isReady())) {
                throw new IllegalStateException("KNX request expired or connection changed; request discarded");
            }
            try {
                T result = operation.run(session.communicator());
                if (generation != adapter.generation() || session != adapter.connection())
                    throw new IllegalStateException("KNX connection changed during request; outcome unknown, not replayed");
                return result;
            }
            finally { lastSendNanos = System.nanoTime(); }
        } finally { lock.unlock(); }
    }

    public boolean readBool(GroupAddress ga) throws Exception { return execute(false, ga, pc -> pc.readBool(ga)); }
    public int readUnsigned(GroupAddress ga, String scale) throws Exception {
        return execute(false, ga, pc -> pc.readUnsigned(ga, scale));
    }
    public double readFloat(GroupAddress ga) throws Exception { return execute(false, ga, pc -> pc.readFloat(ga)); }
    public double readNumeric(Datapoint dp) throws Exception {
        return execute(false, dp.getMainAddress(), pc -> pc.readNumeric(dp));
    }
    private void requireType(GroupAddress ga, String type) {
        if (!adapter.acceptsWriteType(ga, type)) throw new IllegalArgumentException("Unexpected KNX write type for " + ga + ": " + type);
    }
    public void write(GroupAddress ga, boolean value) throws Exception {
        requireType(ga, "1.001");
        execute(true, ga, pc -> { pc.write(ga, value); return null; });
    }
    public void write(GroupAddress ga, int value, String scale) throws Exception {
        if (adapter.acceptsWriteType(ga, "18.001")) {
            if (!io.calimero.process.ProcessCommunication.UNSCALED.equals(scale) || value < 0 || value > 63)
                throw new IllegalArgumentException("Only scene recall 0..63 is permitted");
        } else requireType(ga, scale);
        execute(true, ga, pc -> { pc.write(ga, value, scale); return null; });
    }
    public void write(GroupAddress ga, double value, boolean fourBytes) throws Exception {
        requireType(ga, fourBytes ? "14.000" : "9.004");
        execute(true, ga, pc -> { pc.write(ga, value, fourBytes); return null; });
    }
    public void write(GroupAddress ga, DPTXlator value) throws Exception {
        requireType(ga, value.getType().getID());
        if (value.getType().getID().equals("18.001") && (value.getData()[0] & 0xc0) != 0)
            throw new IllegalArgumentException("Scene storage and reserved bits are not permitted");
        execute(true, ga, pc -> { pc.write(ga, value); return null; });
    }
    public void write(Datapoint dp, String value) throws Exception {
        requireType(dp.getMainAddress(), dp.getDPT());
        if (dp.getDPT().equals("18.001")) throw new IllegalArgumentException("Use numeric scene recall 0..63");
        execute(true, dp.getMainAddress(), pc -> { pc.write(dp, value); return null; });
    }
}
