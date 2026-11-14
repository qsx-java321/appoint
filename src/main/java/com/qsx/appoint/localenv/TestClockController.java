package com.qsx.appoint.localenv;

import com.qsx.appoint.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/v1/test-control/clock")
@Profile("test")
@ConditionalOnProperty(name = "appoint.local-environment.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
public class TestClockController {
    private final AdjustableClock clock;
    public TestClockController(AdjustableClock clock) { this.clock = clock; }
    public record SetTime(@NotNull Instant instant) {}
    public record TimeStatus(ZonedDateTime businessTime, String mode) {}
    @GetMapping public Result<TimeStatus> get() {
        return Result.success(new TimeStatus(ZonedDateTime.now(clock), clock.adjusted() ? "adjusted" : "realtime"));
    }
    @PutMapping public Result<TimeStatus> set(@Valid @RequestBody SetTime request) { clock.set(request.instant()); return get(); }
    @DeleteMapping public Result<TimeStatus> reset() { clock.reset(); return get(); }
}
