package com.qsx.appoint.appointment.application;

import com.qsx.appoint.appointment.entity.AppointmentOrder;
import com.qsx.appoint.appointment.mapper.AppointmentOrderMapper;
import com.qsx.appoint.notification.application.AppointmentNotificationSnapshot;
import com.qsx.appoint.notification.application.AppointmentNotificationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 从预约单读取通知所需的凭证快照，不访问就诊人或医疗目录当前表。 */
@Service
@RequiredArgsConstructor
public class AppointmentNotificationSnapshotSource implements AppointmentNotificationSource {
    private final AppointmentOrderMapper appointments;

    @Override
    public AppointmentNotificationSnapshot find(Long appointmentOrderId) {
        AppointmentOrder appointment = appointments.selectByIdForLookup(appointmentOrderId);
        if (appointment == null) return null;
        return new AppointmentNotificationSnapshot(appointment.getId(), appointment.getUserId(),
                appointment.getPatientName(), appointment.getHospitalName(), appointment.getDepartmentName(),
                appointment.getDoctorName(), appointment.getAppointmentDate(), appointment.getStartTime(),
                appointment.getEndTime(), appointment.getVisitNumber());
    }
}
