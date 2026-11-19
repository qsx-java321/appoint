package com.qsx.appoint.support;

import com.qsx.appoint.appointment.dto.AppointmentResponse;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import com.qsx.appoint.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

@TestComponent
@RequiredArgsConstructor
public class FormalPaymentTestSupport {
    private final BookingLifecycleCoordinator lifecycle;
    private final AppointmentService appointments;
    private final JwtTokenProvider tokens;

    public AppointmentResponse payWithToken(String token, Long appointmentId) {
        return pay(Long.valueOf(tokens.parseClaims(token).getSubject()), appointmentId);
    }

    public AppointmentResponse pay(Long userId, Long appointmentId) {
        lifecycle.payOwned(userId, appointmentId);
        return appointments.getOwned(userId, appointmentId);
    }
}
