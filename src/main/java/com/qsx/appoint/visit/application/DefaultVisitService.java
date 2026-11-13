package com.qsx.appoint.visit.application;

import com.qsx.appoint.appointment.application.AppointmentLifecycle;
import com.qsx.appoint.appointment.application.AppointmentLifecycleCase;
import com.qsx.appoint.appointment.domain.AppointmentStatus;
import com.qsx.appoint.catalog.service.DoctorAccountService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.visit.domain.VisitRules;
import com.qsx.appoint.visit.domain.VisitStatus;
import com.qsx.appoint.visit.entity.VisitRecord;
import com.qsx.appoint.visit.mapper.VisitRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
class DefaultVisitService implements VisitService {
    private final AppointmentLifecycle appointments;
    private final DoctorAccountService doctors;
    private final VisitRecordMapper records;
    private final Clock appointmentClock;

    @Override
    @Transactional
    public void checkIn(Long userId, Long appointmentId) {
        var appointment = requireAppointment(appointmentId);
        if (!appointment.userId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
        var progress = progress(appointmentId);
        if (appointment.status() != AppointmentStatus.CONFIRMED || progress != VisitStatus.NOT_CHECKED_IN) {
            throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
        }
        LocalDateTime now = now();
        if (now.isBefore(VisitRules.checkInOpensAt(appointment.appointmentDate(), appointment.startTime()))) {
            throw new BusinessException(ErrorCode.CHECK_IN_NOT_OPEN);
        }
        if (!now.isBefore(VisitRules.checkInClosesAt(appointment.appointmentDate(), appointment.endTime()))) {
            throw new BusinessException(ErrorCode.CHECK_IN_CLOSED);
        }
        VisitRecord record = new VisitRecord();
        record.setAppointmentOrderId(appointmentId);
        record.setStatus(VisitStatus.WAITING.name());
        record.setCheckedInAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        try {
            records.insert(record);
        } catch (DuplicateKeyException exception) {
            // 并发重复签到也返回资格冲突，保留最先成功的时间。
            throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
        }
    }

    @Override
    @Transactional
    public void startVisit(Long doctorUserId, Long appointmentId) {
        requireDoctorAppointment(doctorUserId, appointmentId);
        if (!VisitRules.canStart(progress(appointmentId))) throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
        requireOne(records.startIfWaiting(appointmentId, now()));
    }

    @Override
    @Transactional
    public void completeVisit(Long doctorUserId, Long appointmentId) {
        requireDoctorAppointment(doctorUserId, appointmentId);
        if (!VisitRules.canComplete(progress(appointmentId))) throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
        requireOne(records.completeIfInProgress(appointmentId, now()));
    }

    private AppointmentLifecycleCase requireAppointment(Long appointmentId) {
        var appointment = appointments.find(appointmentId);
        if (appointment == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return appointment;
    }
    private void requireDoctorAppointment(Long doctorUserId, Long appointmentId) {
        Long doctorId = doctors.requireDoctorId(doctorUserId);
        var appointment = requireAppointment(appointmentId);
        if (!doctorId.equals(appointment.doctorId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (appointment.status() != AppointmentStatus.CONFIRMED) throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
    }
    private VisitStatus progress(Long appointmentId) {
        var record = records.selectByAppointmentOrderId(appointmentId);
        return record == null ? VisitStatus.NOT_CHECKED_IN : VisitStatus.fromStored(record.getStatus());
    }
    private LocalDateTime now() { return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS); }
    private void requireOne(int count) {
        if (count != 1) throw new BusinessException(ErrorCode.VISIT_STATE_ERROR);
    }
}
