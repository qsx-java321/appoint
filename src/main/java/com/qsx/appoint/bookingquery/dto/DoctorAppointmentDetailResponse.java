package com.qsx.appoint.bookingquery.dto;
import com.qsx.appoint.appointment.dto.AppointmentDoctorResponse;
import java.time.LocalDate;
import java.time.LocalTime;
public record DoctorAppointmentDetailResponse(Long appointmentId, String appointmentNo, Integer visitNumber,
        LocalDate appointmentDate, String sessionType, LocalTime startTime, LocalTime endTime,
        DoctorPatientResponse patient, AppointmentDoctorResponse doctor, DoctorVisitProgressResponse visit) {}
