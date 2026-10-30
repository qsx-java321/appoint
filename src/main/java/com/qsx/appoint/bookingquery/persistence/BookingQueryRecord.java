package com.qsx.appoint.bookingquery.persistence;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** appointment/payment/schedule 的只读组合行，仅属于 bookingquery。 */
@Getter
@Setter
public class BookingQueryRecord {
    private Long id;
    private String appointmentNo;
    private Long appointmentUserId;
    private Long scheduleId;
    private Long patientId;
    private String patientName;
    private String patientDocumentType;
    private String patientDocumentNo;
    private String patientContactPhone;
    private String patientContactEmail;
    private Long doctorId;
    private String doctorNo;
    private String doctorName;
    private String doctorTitle;
    private Long departmentId;
    private String departmentName;
    private Long hospitalId;
    private String hospitalName;
    private String hospitalAddress;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Integer visitNumber;
    private String scheduleStatus;
    private LocalDateTime createdAt;
    private LocalDateTime paymentExpiresAt;
    private LocalDateTime closedAt;
    private Long paymentOrderId;
    private Long paymentUserId;
    private String paymentStatus;
    private BigDecimal amount;
    private String currency;
    private Integer paymentAttemptCount;
    private Long attemptId;
    private String attemptNo;
    private String attemptChannel;
    private String attemptStatus;
    private String providerOrderNo;
    private String attemptFailureCode;
    private LocalDateTime attemptCreatedAt;
    private LocalDateTime attemptCompletedAt;
    private Long refundId;
    private String refundNo;
    private BigDecimal refundAmount;
    private String refundCurrency;
    private String refundChannel;
    private String refundStatus;
    private String refundReason;
    private Integer refundAttemptCount;
    private String refundLastFailureCode;
    private LocalDateTime refundCompletedAt;
}
