package com.qsx.appoint.bookingquery.application;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.bookingquery.persistence.BookingQueryMapper;
import com.qsx.appoint.bookingquery.persistence.BookingQueryRecord;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 预约详情/列表的跨模块只读实现；不参与任何写事务。 */
@Service
@RequiredArgsConstructor
public class DefaultBookingQueryService implements AppointmentService {
    private final BookingQueryMapper mapper;
    private final Clock appointmentClock;
    private final AppointmentResponseAssembler assembler;

    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        BookingQueryRecord record = mapper.selectById(orderId);
        ensureOwner(userId, record);
        return assembler.assemble(record, now());
    }

    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        LocalDateTime serverTime = now();
        return mapper.selectByUserId(userId).stream()
                .map(record -> assembler.assemble(record, serverTime))
                .toList();
    }

    private void ensureOwner(Long userId, BookingQueryRecord record) {
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!record.getAppointmentUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}
