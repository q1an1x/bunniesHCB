package es.buni.hcb.adapters.knx;

import java.time.Duration;
import java.util.Objects;

public record KnxSettings(KnxMode mode, String gateway, int port, boolean nat,
                          Duration responseTimeout, Duration minSendInterval,
                          Duration initialBackoff, Duration maxBackoff, Duration silenceTimeout,
                          boolean automationsEnabled, boolean timeServiceEnabled) {
    public KnxSettings {
        Objects.requireNonNull(mode);
        for (Duration duration : new Duration[]{responseTimeout, initialBackoff, maxBackoff, silenceTimeout}) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("KNX timeouts must be positive");
            }
        }
        if (minSendInterval == null || minSendInterval.isNegative()) {
            throw new IllegalArgumentException("KNX send interval must not be negative");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0 || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid KNX port or reconnect bounds");
        }
        if (mode != KnxMode.OFFLINE && (gateway == null || gateway.isBlank())) {
            throw new IllegalArgumentException("--knx-gateway is required for observe/live mode");
        }
    }

    public static KnxSettings defaults(KnxMode mode, String gateway) {
        return new KnxSettings(mode, gateway, 3671, false, Duration.ofSeconds(2),
                Duration.ofMillis(100), Duration.ofSeconds(1), Duration.ofSeconds(60), Duration.ofSeconds(45), false, false);
    }
}
