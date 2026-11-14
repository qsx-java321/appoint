package com.qsx.appoint.localenv;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

/** 时间设定后使用单调时钟持续走秒；恢复实时和重新设时均为原子操作。 */
public final class AdjustableClock extends Clock {
    private record Anchor(Instant instant, long nanos) {}
    private final ZoneId zone;
    private final AtomicReference<Anchor> anchor;
    public AdjustableClock(ZoneId zone) { this(zone, new AtomicReference<>()); }
    private AdjustableClock(ZoneId zone, AtomicReference<Anchor> anchor) { this.zone = zone; this.anchor = anchor; }
    public void set(Instant instant) { anchor.set(new Anchor(java.util.Objects.requireNonNull(instant), System.nanoTime())); }
    public void reset() { anchor.set(null); }
    public boolean adjusted() { return anchor.get() != null; }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new AdjustableClock(zone, anchor); }
    @Override public Instant instant() {
        Anchor value = anchor.get();
        return value == null ? Instant.now() : value.instant().plusNanos(System.nanoTime() - value.nanos());
    }
}
