package com.qsx.appoint.appointment.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ExpirationCandidate {
    private Long id;
    private LocalDateTime paymentExpiresAt;
}
