package com.qsx.appoint.bookingquery.application;
import com.qsx.appoint.bookingquery.dto.*;
import com.qsx.appoint.catalog.model.AppointmentSession;
import java.time.LocalDate;
import java.util.List;
public interface DoctorVisitQueryService {
    List<DoctorSessionResponse> listSessions(Long doctorUserId, LocalDate date);
    List<DoctorAppointmentRowResponse> listAppointments(Long doctorUserId, LocalDate date, AppointmentSession sessionType);
    DoctorAppointmentDetailResponse getAppointment(Long doctorUserId, Long appointmentId);
}
