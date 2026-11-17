package com.qsx.appoint.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("appoint.registration")
public record RegistrationProperties(@DefaultValue("local") String namespace,
        @DefaultValue("no-reply@appoint.local") String from,
        @DefaultValue("5m") Duration codeTtl, @DefaultValue("5m") Duration ticketTtl) {}
