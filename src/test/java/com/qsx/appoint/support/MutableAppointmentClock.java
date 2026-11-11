package com.qsx.appoint.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 正常时间边界测试使用的上海业务时钟，不向生产代码提供调时能力。 */
public final class MutableAppointmentClock extends Clock {
    private volatile Instant current;

    public MutableAppointmentClock(String localTime) { set(localTime); }

    public void set(String localTime) {
        current = LocalDateTime.parse(localTime).atZone(getZone()).toInstant();
    }

    @Override public ZoneId getZone() { return ZoneId.of("Asia/Shanghai"); }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
    @Override public Instant instant() { return current; }
}
