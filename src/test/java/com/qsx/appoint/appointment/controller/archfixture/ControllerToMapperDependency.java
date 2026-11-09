package com.qsx.appoint.appointment.controller.archfixture;

@org.springframework.context.annotation.Profile("architecture-fixture")
@org.springframework.web.bind.annotation.RestController
public class ControllerToMapperDependency {
    private com.qsx.appoint.appointment.mapper.AppointmentOrderMapper mapper;
}
