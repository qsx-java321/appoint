package com.qsx.appoint.bookingquery.persistence;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
/** 医生页面的只读预约快照和共享就诊进度，不作为写入对象。 */
@Getter @Setter
public class DoctorVisitQueryRecord {
    private Long appointmentId;
    private String appointmentNo;
    private Integer visitNumber;
    private LocalDate appointmentDate;
    private String sessionType;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long patientId;
    private String patientName;
    private String patientDocumentType;
    private String patientDocumentNo;
    private Long doctorId;
    private String doctorNo;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private Long hospitalId;
    private String hospitalName;
    private String hospitalAddress;
    private String visitStatus;
    private LocalDateTime checkedInAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
