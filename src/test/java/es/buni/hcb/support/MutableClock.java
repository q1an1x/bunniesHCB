package es.buni.hcb.support;
import java.time.*;
public final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-14T04:00:00Z");
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    public void advance(Duration amount) { now = now.plus(amount); }
    public void set(Instant value) { now = value; }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId value) { return Clock.fixed(now, value); }
    @Override public Instant instant() { return now; }
}
