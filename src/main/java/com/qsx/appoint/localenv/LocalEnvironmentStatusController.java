package com.qsx.appoint.localenv;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "appoint.local-environment.enabled", havingValue = "true")
public class LocalEnvironmentStatusController {
    public static final String PATH = "/api/v1/local-environment";

    private final LocalEnvironmentLifecycle lifecycle;
    private final ApplicationAvailability availability;
    private final String launchId;

    public LocalEnvironmentStatusController(LocalEnvironmentLifecycle lifecycle, ApplicationAvailability availability,
                                            Environment environment) {
        this.lifecycle = lifecycle;
        this.availability = availability;
        this.launchId = environment.getProperty("APPOINT_LOCAL_LAUNCH_ID");
    }

    public record Status(String profile, boolean ready, String launchId) {}

    @GetMapping(PATH)
    public ResponseEntity<Status> status() {
        boolean ready = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready ? 200 : 503).cacheControl(CacheControl.noStore())
                .body(new Status(lifecycle.name(), ready, launchId));
    }
}
