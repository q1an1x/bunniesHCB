package es.buni.hcb.adapters.knx;

import es.buni.hcb.adapters.Adapter;
import es.buni.hcb.adapters.knx.entities.KNXEntity;
import es.buni.hcb.adapters.knx.services.KNXTimeService;
import es.buni.hcb.config.KNXEntities;
import es.buni.hcb.automation.PolicyKind;
import es.buni.hcb.automation.ManualOverrides;
import es.buni.hcb.automation.modes.*;
import es.buni.hcb.core.EntityRegistry;
import es.buni.hcb.core.Lifecycle;
import es.buni.hcb.core.NetworkContext;
import es.buni.hcb.utils.Logger;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class KNXAdapter extends Adapter implements Reconnectable {
    public enum ConnectionState { STOPPED, CONNECTING, SYNCHRONIZING, CONNECTED, RECONNECTING }
    private final KnxSettings settings;
    private final KnxConnectionFactory factory;
    private final Clock clock;
    private final KnxBus bus = new KnxBus(this);
    private final Map<GroupAddress, Set<KNXEntity>> entitiesByGroupAddress = new ConcurrentHashMap<>();
    private final List<Lifecycle> services = new ArrayList<>();
    private final List<KnxBinding> serviceBindings = new ArrayList<>();
    private final Map<GroupAddress, Set<String>> writeTypes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("knx-reconnect").factory());
    private final ThreadPoolExecutor events = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(512), Thread.ofPlatform().daemon().name("knx-events").factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService automationScheduler = Executors.newScheduledThreadPool(2,
            Thread.ofPlatform().daemon().name("knx-automation-", 0).factory());
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong receivedTelegrams = new AtomicLong();
    private final AtomicLong droppedTelegrams = new AtomicLong();
    private final ThreadLocal<Long> processingGeneration = new ThreadLocal<>();
    private volatile KnxConnection connection;
    private volatile ConnectionState state = ConnectionState.STOPPED;
    private volatile boolean closed;
    private boolean configured;
    private boolean servicesStarted;
    private ScheduledFuture<?> reconnectTask;
    private long backoffMillis;
    private final HealthMonitor healthMonitor;
    private HouseModeController houseModes;
    private final ManualOverrides manualOverrides = new ManualOverrides(this);

    public KNXAdapter(EntityRegistry registry, NetworkContext network) {
        this(registry, network, KnxSettings.defaults(KnxMode.OFFLINE, null));
    }

    public KNXAdapter(EntityRegistry registry, NetworkContext network, KnxSettings settings) {
        this(registry, settings, new CalimeroConnectionFactory(network, settings), Clock.systemDefaultZone());
    }

    public KNXAdapter(EntityRegistry registry, KnxSettings settings, KnxConnectionFactory factory, Clock clock) {
        super("knx", registry);
        this.settings = Objects.requireNonNull(settings);
        this.factory = Objects.requireNonNull(factory);
        this.clock = Objects.requireNonNull(clock);
        backoffMillis = settings.initialBackoff().toMillis();
        healthMonitor = new HealthMonitor(this);
        registry.getEventBus().onOverflow(() -> reconnect("domain event buffer exhausted"));
    }

    /** Safe offline: no network, timers, HomeKit registration, or device writes. */
    public synchronized void configure() throws Exception {
        if (configured) return;
        if (closed) throw new IllegalStateException("Adapter was shut down");
        KNXEntities.registerAll(this);
        configureHouseModes();
        registerService(new KNXTimeService(this, 0, 6, 123, 0, 6, 124));
        for (KnxBinding binding : bindings()) if (binding.writable()) allowWrite(binding.address(), binding.dpt());
        configured = true;
    }

    public synchronized void configureHouseModes() {
        if (houseModes != null) return;
        if (servicesStarted) throw new IllegalStateException("Configure modes before startup");
        houseModes = new HouseModeController(this);
        for (HouseMode mode : HouseMode.values()) super.register(new ModeAccessory(this, houseModes, mode));
        services.addFirst(houseModes); // Load the mode gate before starting policies.
    }

    public void register(KNXEntity entity) {
        super.register(entity);
        for (GroupAddress address : entity.groupAddresses()) {
            entitiesByGroupAddress.computeIfAbsent(address, ignored -> ConcurrentHashMap.newKeySet()).add(entity);
        }
        entity.bindings().stream().filter(KnxBinding::writable).forEach(b -> allowWrite(b.address(), b.dpt()));
        if (entity instanceof es.buni.hcb.adapters.knx.entities.lighting.Light) {
            for (KnxBinding binding : entity.bindings()) if (binding.writable())
                manualOverrides.register(entity.getLocation(), binding.address(), binding.property().equals("colorTemperature")
                        ? ManualOverrides.COLOR : ManualOverrides.LIGHT_LEVEL);
        }
    }

    public synchronized void registerService(Lifecycle service) {
        if (servicesStarted) throw new IllegalStateException("Cannot register services after startup");
        services.add(Objects.requireNonNull(service));
    }

    public void declareCommand(String owner, String property, GroupAddress address, String dpt) {
        serviceBindings.add(new KnxBinding(owner, property, address, dpt, KnxBinding.Role.COMMAND));
        allowWrite(address, dpt);
    }
    public void declarePolicyCommand(String owner, String room, String property, GroupAddress address, String dpt) {
        declareCommand(owner, property, address, dpt);
        if (!dpt.equals("18.001")) manualOverrides.register(room, address, dpt.equals("7.600")
                ? ManualOverrides.COLOR : ManualOverrides.LIGHT_LEVEL);
    }

    public List<KnxBinding> bindings() {
        var result = new ArrayList<>(serviceBindings);
        for (var entity : entities()) if (entity instanceof KNXEntity knx) result.addAll(knx.bindings());
        return result.stream().sorted(Comparator.comparing(KnxBinding::owner)
                .thenComparing(KnxBinding::property)).toList();
    }

    @Override public void start() throws Exception {
        configure();
        synchronized (this) {
            if (closed) throw new IllegalStateException("Adapter was shut down");
            if (state != ConnectionState.STOPPED) return;
            if (settings.mode() == KnxMode.OFFLINE) return;
            state = ConnectionState.CONNECTING;
        }
        connect();
        healthMonitor.start();
    }

    private void connect() {
        final long epoch;
        synchronized (this) {
            if (closed) return;
            epoch = generation.incrementAndGet();
        }
        KnxConnection opened = null;
        try {
            opened = factory.connect(e -> receive(e, epoch), () -> disconnected(epoch));
            synchronized (this) {
                if (closed || epoch != generation.get()) { opened.close(); return; }
                if (!opened.isOpen()) throw new IllegalStateException("New KNX session is already closed");
                connection = opened;
                state = ConnectionState.SYNCHRONIZING;
            }
            if (settings.mode() == KnxMode.LIVE) {
                for (var entity : entities().stream().sorted(Comparator.comparing(e -> e.getNamedId())).toList()) {
                    if (closed || epoch != generation.get()) return;
                    try {
                        processingGeneration.set(epoch);
                        entity.initialize();
                    }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
                    catch (Exception e) { Logger.warn("KNX state unavailable: " + entity.getNamedId() + ": " + e.getMessage()); }
                    finally { processingGeneration.remove(); }
                }
            }
            synchronized (this) {
                if (closed || epoch != generation.get()) return;
                if (!opened.isOpen()) throw new IllegalStateException("KNX session closed during synchronization");
                state = ConnectionState.CONNECTED;
                backoffMillis = settings.initialBackoff().toMillis();
                healthMonitor.reset();
                if (settings.mode() == KnxMode.LIVE && !servicesStarted) {
                    servicesStarted = true;
                    for (Lifecycle service : services) {
                        if (service instanceof KNXTimeService ? settings.timeServiceEnabled() : settings.automationsEnabled()) service.start();
                    }
                }
            }
            Logger.info("KNX connected (" + settings.mode() + "); existing entities retained");
        } catch (Exception e) {
            if (opened != null) opened.close();
            Logger.warn("KNX connection failed: " + e.getMessage());
            reconnect("connection or synchronization failed");
        }
    }

    private void disconnected(long epoch) {
        if (epoch == generation.get()) reconnect("transport closed");
    }

    @Override public synchronized void reconnect(String reason) {
        if (closed || settings.mode() == KnxMode.OFFLINE || state == ConnectionState.STOPPED) return;
        if (state == ConnectionState.RECONNECTING && reconnectTask != null && !reconnectTask.isDone()) return;
        state = ConnectionState.RECONNECTING;
        generation.incrementAndGet();
        if (houseModes != null) houseModes.connectionLost();
        KnxConnection old = connection;
        connection = null;
        for (var entity : entities()) if (entity instanceof KNXEntity knx) knx.invalidateState();
        events.getQueue().clear();
        registry.getEventBus().clearPending();
        if (old != null) old.close();
        long delay = backoffMillis;
        backoffMillis = Math.min(backoffMillis * 2, settings.maxBackoff().toMillis());
        Logger.warn("KNX reconnect in " + delay + "ms: " + reason);
        reconnectTask = reconnectExecutor.schedule(() -> {
            synchronized (this) { if (closed) return; state = ConnectionState.CONNECTING; }
            connect();
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void receive(ProcessEvent event, long epoch) {
        if (closed || epoch != generation.get()) return;
        receivedTelegrams.incrementAndGet();
        healthMonitor.receivedTelegram();
        try {
            events.execute(() -> runInSession(epoch, () -> {
                manualOverrides.observe(event);
                var listeners = entitiesByGroupAddress.getOrDefault(event.getDestination(), Set.of());
                for (KNXEntity entity : listeners) {
                    try { entity.handleBusUpdate(event.getDestination(), event); }
                    catch (Exception e) { Logger.error("KNX update failed: " + entity.getNamedId(), e); }
                }
            }));
        } catch (RejectedExecutionException e) {
            if (!closed) {
                droppedTelegrams.incrementAndGet();
                reconnect("event buffer exhausted; cached state invalidated");
            }
        }
    }

    @Override public void stop() throws Exception {
        KnxConnection old;
        synchronized (this) {
            if (closed) return;
            closed = true;
            state = ConnectionState.STOPPED;
            generation.incrementAndGet();
            if (reconnectTask != null) reconnectTask.cancel(true);
            old = connection;
            connection = null;
        }
        healthMonitor.shutdown();
        for (Lifecycle service : services.reversed()) {
            try { service.stop(); } catch (Exception e) { Logger.error("KNX service shutdown failed", e); }
        }
        if (old != null) old.close();
        reconnectExecutor.shutdownNow();
        events.shutdownNow();
        automationScheduler.shutdownNow();
        super.stop();
        entitiesByGroupAddress.clear();
    }

    public KnxBus bus() { return bus; }
    public HouseModeController houseModes() { return houseModes; }
    public long intentRevision() { return houseModes == null ? 0 : houseModes.selectionRevision(); }
    public ManualOverrides manualOverrides() { return manualOverrides; }
    public boolean permitsPolicy(String room, PolicyKind kind) {
        return (houseModes == null || houseModes.permits(room, kind)) && manualOverrides.permits(room, kind);
    }
    public KnxSettings settings() { return settings; }
    public ScheduledExecutorService scheduler() { return automationScheduler; }
    public boolean isLocalSource(ProcessEvent event) {
        var session = connection;
        return session != null && session.isLocalSource(event.getSourceAddr());
    }
    public Clock clock() { return clock; }
    public long processingGeneration() {
        Long active = processingGeneration.get();
        return active == null ? generation.get() : active;
    }
    public void runInSession(long epoch, Runnable action) {
        if (closed || epoch != generation.get()) return;
        Long previous = processingGeneration.get();
        processingGeneration.set(epoch);
        try { action.run(); }
        finally {
            if (previous == null) processingGeneration.remove();
            else processingGeneration.set(previous);
        }
    }
    public long generation() { return generation.get(); }
    KnxConnection connection() { return connection; }
    private void allowWrite(GroupAddress address, String dpt) {
        writeTypes.computeIfAbsent(address, ignored -> ConcurrentHashMap.newKeySet()).add(dpt);
    }
    boolean isWriteAllowed(GroupAddress address) { return writeTypes.containsKey(address); }
    boolean acceptsWriteType(GroupAddress address, String type) {
        return writeTypes.getOrDefault(address, Set.of()).stream().anyMatch(expected ->
                expected.equals(type) || (expected.startsWith("1.") && type.startsWith("1.")));
    }
    public boolean isReady() { return !closed && state == ConnectionState.CONNECTED; }
    public boolean isAutomationReady() { return isReady() && settings.mode() == KnxMode.LIVE && settings.automationsEnabled(); }
    public ConnectionState connectionState() { return state; }
    public long receivedTelegrams() { return receivedTelegrams.get(); }
    public long droppedTelegrams() { return droppedTelegrams.get(); }
}
