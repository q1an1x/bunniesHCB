package es.buni.hcb.automation;

import es.buni.hcb.adapters.knx.KNXAdapter;
import io.calimero.GroupAddress;
import io.calimero.process.ProcessEvent;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Timed room-level ownership: feedback is never mistaken for a manual command. */
public final class ManualOverrides {
    public static final Set<PolicyKind> LIGHT_LEVEL = Set.of(PolicyKind.PRESENCE, PolicyKind.CONSTANT_LIGHT, PolicyKind.NIGHT_LIGHT);
    // Presence/night scene recalls may also contain a DALI color-temperature value.
    public static final Set<PolicyKind> COLOR = Set.of(PolicyKind.ADAPTIVE_COLOR, PolicyKind.PRESENCE, PolicyKind.NIGHT_LIGHT);
    private enum Input {
        COMMAND, RELATIVE_STEP, OFF;
        boolean accepts(byte[] data) {
            if (this == COMMAND) return true;
            if (data.length != 1) return false;
            int value = Byte.toUnsignedInt(data[0]);
            // DPT 3: both 0 and 8 are stop telegrams. They do not extend a hold.
            return this == OFF ? value == 0 : value <= 15 && (value & 7) != 0;
        }
    }
    private record Target(String room, Set<PolicyKind> kinds, Input input) { }
    private record Key(String room, PolicyKind kind) { }
    private final KNXAdapter adapter;
    private final Map<GroupAddress, Set<Target>> commands = new ConcurrentHashMap<>();
    private final Map<Key, Instant> until = new ConcurrentHashMap<>();
    private final Map<String, java.util.concurrent.atomic.AtomicLong> revisions = new ConcurrentHashMap<>();
    private Duration duration = Duration.ofMinutes(30);

    public ManualOverrides(KNXAdapter adapter) { this.adapter = adapter; }
    public void duration(Duration value) {
        if (value.isNegative() || value.isZero()) throw new IllegalArgumentException("Manual hold must be positive");
        duration = value;
    }
    public void register(String room, GroupAddress address, Set<PolicyKind> kinds) {
        register(room, address, kinds, Input.COMMAND);
    }
    public void registerRelative(String room, GroupAddress address, Set<PolicyKind> kinds) {
        register(room, address, kinds, Input.RELATIVE_STEP);
    }
    public void registerOff(String room, GroupAddress address) {
        register(room, address, LIGHT_LEVEL, Input.OFF);
    }
    private void register(String room, GroupAddress address, Set<PolicyKind> kinds, Input input) {
        commands.computeIfAbsent(address, ignored -> ConcurrentHashMap.newKeySet()).add(new Target(room, Set.copyOf(kinds), input));
    }
    public void observe(ProcessEvent event) {
        if (event.getServiceCode() != 0x80 || adapter.isLocalSource(event)) return;
        for (Target target : commands.getOrDefault(event.getDestination(), Set.of()))
            if (target.input().accepts(event.getASDU())) hold(target.room(), target.kinds());
    }
    public void hold(String room, Set<PolicyKind> kinds) {
        revisions.computeIfAbsent(room, ignored -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        Instant expiry = adapter.clock().instant().plus(duration);
        for (PolicyKind kind : kinds) until.put(new Key(room, kind), expiry);
    }
    public long revision(String room) {
        var revision = revisions.get(room); return revision == null ? 0 : revision.get();
    }
    public boolean permits(String room, PolicyKind kind) {
        Key key = new Key(room, kind);
        Instant expiry = until.get(key);
        if (expiry == null) return true;
        if (adapter.clock().instant().isBefore(expiry)) return false;
        until.remove(key, expiry);
        return true;
    }
    public void resume(String room) { until.keySet().removeIf(key -> key.room().equals(room)); }
    public void resume(String room, PolicyKind kind) { until.remove(new Key(room, kind)); }
    public Map<String, Map<String, String>> active() {
        var result = new TreeMap<String, Map<String, String>>();
        for (var entry : until.entrySet()) {
            Key key = entry.getKey();
            if (!permits(key.room(), key.kind())) result.computeIfAbsent(key.room(), ignored -> new TreeMap<>())
                    .put(key.kind().name(), entry.getValue().toString());
        }
        return result;
    }
}
