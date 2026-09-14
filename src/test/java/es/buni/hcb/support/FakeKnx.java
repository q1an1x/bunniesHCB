package es.buni.hcb.support;

import es.buni.hcb.adapters.knx.*;
import es.buni.hcb.core.EntityRegistry;
import io.calimero.*;
import io.calimero.datapoint.Datapoint;
import io.calimero.process.*;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

/** No socket creation: only typed protocol calls and synthetic telegrams. */
public final class FakeKnx implements KnxConnectionFactory, AutoCloseable {
    public final MutableClock clock = new MutableClock();
    public final List<String> writes = new CopyOnWriteArrayList<>();
    public final List<String> reads = new CopyOnWriteArrayList<>();
    public final AtomicInteger connections = new AtomicInteger();
    public volatile boolean failWrites, failColorRead;
    public volatile int failConnections;
    public volatile int failAfterWrites = Integer.MAX_VALUE;
    public final KNXAdapter adapter;
    public volatile Session latest;
    public ScheduledExecutorService timersOverride;

    public FakeKnx(KnxMode mode, boolean automations) {
        var settings = new KnxSettings(mode, mode == KnxMode.OFFLINE ? null : "fake.invalid", 3671, false,
                Duration.ofMillis(50), Duration.ZERO, Duration.ofMillis(10), Duration.ofMillis(40), Duration.ofSeconds(45), automations, false);
        adapter = new KNXAdapter(new EntityRegistry(), settings, this, clock) {
            @Override public synchronized void configure() { }
            @Override public ScheduledExecutorService scheduler() { return timersOverride == null ? super.scheduler() : timersOverride; }
        };
    }
    public FakeKnx() { this(KnxMode.LIVE, true); }
    @Override public KnxConnection connect(Consumer<ProcessEvent> receiver, Runnable disconnected) {
        connections.incrementAndGet();
        if (failConnections-- > 0) throw new IllegalStateException("simulated offline gateway");
        latest = new Session(receiver, disconnected);
        return latest;
    }
    public final class Session implements KnxConnection {
        private final Consumer<ProcessEvent> receiver;
        private final Runnable disconnected;
        public volatile boolean open = true;
        public final ProcessCommunicator pc = (ProcessCommunicator) Proxy.newProxyInstance(ProcessCommunicator.class.getClassLoader(),
                new Class<?>[]{ProcessCommunicator.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("toString")) return "FakeProcessCommunicator";
                    if (name.equals("hashCode")) return System.identityHashCode(proxy);
                    if (name.equals("equals")) return proxy == args[0];
                    if (name.startsWith("read")) {
                        reads.add(name + ":" + args[0]);
                        if (name.equals("readBool")) return false;
                        if (name.equals("readUnsigned")) return 0;
                        if (name.equals("readFloat")) return 120.0;
                        if (name.equals("readNumeric")) {
                            if (failColorRead) throw new KNXTimeoutException("missing color feedback");
                            return 4000.0;
                        }
                    }
                    if (name.equals("write")) {
                        if (failWrites || writes.size() >= failAfterWrites) throw new KNXTimeoutException("simulated write failure");
                        String address = args[0] instanceof Datapoint dp ? dp.getMainAddress().toString() : args[0].toString();
                        writes.add(address + "=" + args[1]);
                    }
                    return null;
                });
        Session(Consumer<ProcessEvent> receiver, Runnable disconnected) { this.receiver = receiver; this.disconnected = disconnected; }
        @Override public ProcessCommunicator communicator() { return pc; }
        @Override public boolean isOpen() { return open; }
        @Override public boolean isLocalSource(IndividualAddress source) { return source.toString().equals("1.1.240"); }
        @Override public void close() { open = false; }
        public void disconnect() { open = false; disconnected.run(); }
        public void receive(String ga, int service, byte... data) throws Exception { receiver.accept(event(ga, service, data)); }
    }
    public ProcessEvent event(String ga, int service, byte... data) throws Exception {
        return new ProcessEvent(latest.pc, new IndividualAddress("1.1.10"), new GroupAddress(ga), service, data, data.length == 1);
    }
    public void start() throws Exception { adapter.start(); }
    public void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError("Timed out waiting for fake runtime");
            TimeUnit.MILLISECONDS.sleep(5);
        }
    }
    @Override public void close() throws Exception { adapter.stop(); adapter.getRegistry().getEventBus().close(); }
}
