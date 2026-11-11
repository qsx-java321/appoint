package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.bookingquery.dto.*;
import com.qsx.appoint.bookingquery.persistence.DoctorVisitQueryRecord;
import com.qsx.appoint.patient.service.ResidentIdCard;
import com.qsx.appoint.visit.domain.VisitRules;
import com.qsx.appoint.visit.domain.VisitStatus;
import org.springframework.stereotype.Component;

@Component
class DoctorVisitResponseAssembler {
    DoctorAppointmentRowResponse row(DoctorVisitQueryRecord record) {
        var progress = progress(record);
        return new DoctorAppointmentRowResponse(record.getAppointmentId(), record.getVisitNumber(), record.getPatientName(),
                progress.name(), record.getCheckedInAt(), VisitRules.canStart(progress), VisitRules.canComplete(progress));
    }
    DoctorAppointmentDetailResponse detail(DoctorVisitQueryRecord record) {
        var patient = new DoctorPatientResponse(record.getPatientId(), record.getPatientName(),
                record.getPatientDocumentType(), ResidentIdCard.mask(record.getPatientDocumentNo()));
        var doctor = new AppointmentDoctorResponse(record.getDoctorId(), record.getDoctorNo(), record.getDoctorName(),
                record.getDoctorTitle(), new AppointmentDepartmentResponse(record.getDepartmentId(), record.getDepartmentName()),
                new AppointmentHospitalResponse(record.getHospitalId(), record.getHospitalName(), record.getHospitalAddress()));
        var visit = new DoctorVisitProgressResponse(progress(record).name(), record.getCheckedInAt(), record.getStartedAt(), record.getCompletedAt());
        return new DoctorAppointmentDetailResponse(record.getAppointmentId(), record.getAppointmentNo(), record.getVisitNumber(),
                record.getAppointmentDate(), record.getSessionType(), record.getStartTime(), record.getEndTime(), patient, doctor, visit);
    }
    private VisitStatus progress(DoctorVisitQueryRecord record) {
        return record.getVisitStatus() == null ? VisitStatus.NOT_CHECKED_IN : VisitStatus.fromStored(record.getVisitStatus());
    }
}
