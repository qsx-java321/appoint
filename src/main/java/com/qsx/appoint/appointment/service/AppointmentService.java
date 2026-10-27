package com.qsx.appoint.appointment.service;

import com.qsx.appoint.appointment.dto.*;
import java.util.List;

/** 预约创建和订单归属查询的业务接口。 */
public interface AppointmentService {
    AppointmentCreationResult create(Long userId, String idempotencyKey, CreateAppointmentRequest request);
    AppointmentResponse getOwned(Long userId, Long orderId);
    List<AppointmentResponse> listMine(Long userId);
    AppointmentResponse cancel(Long userId, Long orderId);
}
