package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.bookingquery.dto.*;
import com.qsx.appoint.bookingquery.persistence.DoctorVisitQueryMapper;
import com.qsx.appoint.bookingquery.persistence.DoctorSessionCountRecord;
import com.qsx.appoint.catalog.model.AppointmentSession;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
class DefaultDoctorVisitQueryService implements DoctorVisitQueryService {
    private final DoctorAccountService doctors;
    private final DoctorVisitQueryMapper mapper;
    private final DoctorVisitResponseAssembler assembler;
    private final Clock appointmentClock;

    @Override
    public List<DoctorSessionResponse> listSessions(Long doctorUserId, LocalDate date) {
        Long doctorId = doctors.requireDoctorId(doctorUserId);
        LocalDate day = date == null ? LocalDate.now(appointmentClock) : date;
        var counts = new HashMap<String, DoctorSessionCountRecord>();
        mapper.countByDoctorAndDate(doctorId, day).forEach(row -> counts.put(row.getSessionType(), row));
        var result = new ArrayList<DoctorSessionResponse>(4);
        for (var session : AppointmentSession.values()) {
            var count = counts.getOrDefault(session.name(), new DoctorSessionCountRecord());
            result.add(new DoctorSessionResponse(day, session.name(), session.startTime(), session.endTime(),
                    count.getConfirmedCount(), count.getNotCheckedInCount(), count.getWaitingCount(),
                    count.getInProgressCount(), count.getCompletedCount()));
        }
        return result;
    }
    @Override
    public List<DoctorAppointmentRowResponse> listAppointments(Long doctorUserId, LocalDate date, AppointmentSession sessionType) {
        Long doctorId = doctors.requireDoctorId(doctorUserId);
        return mapper.selectByDoctorDateSession(doctorId, date, sessionType.name()).stream().map(assembler::row).toList();
    }
    @Override
    public DoctorAppointmentDetailResponse getAppointment(Long doctorUserId, Long appointmentId) {
        Long doctorId = doctors.requireDoctorId(doctorUserId);
        var record = mapper.selectOwnedDetail(doctorId, appointmentId);
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return assembler.detail(record);
    }
}
