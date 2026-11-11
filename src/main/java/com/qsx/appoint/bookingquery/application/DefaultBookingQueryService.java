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

/**
 * 跨模块只读查询用例：读取组合行、检查归属，再委托Assembler返回详情。
 * <p>不参加业务写事务或获取行锁；详情先判不存在/非本人，不能用资金完整性错误泄露他人数据。
 * @see AppointmentResponseAssembler
 */
@Service
@RequiredArgsConstructor
public class DefaultBookingQueryService implements AppointmentService {
    private final BookingQueryMapper mapper;
    private final Clock appointmentClock;
    private final AppointmentResponseAssembler assembler;
    /** {@inheritDoc} */
    @Override
    public AppointmentResponse getOwned(Long userId, Long orderId) {
        BookingQueryRecord record = mapper.selectById(orderId);
        ensureOwner(userId, record);
        return assembler.assemble(record, now());
    }
    /** {@inheritDoc} */
    @Override
    public List<AppointmentResponse> listMine(Long userId) {
        return listMine(userId, AppointmentTimeOrder.DESC);
    }
    @Override
    public List<AppointmentResponse> listMine(Long userId, AppointmentTimeOrder order) {
        // 一次列表共享时间采样，不能逐行取时导致同一次响应对截止边界判断不同。
        LocalDateTime serverTime = now();
        var records = order == AppointmentTimeOrder.ASC ? mapper.selectByUserIdAsc(userId) : mapper.selectByUserId(userId);
        return records.stream()
                .map(record -> assembler.assemble(record, serverTime))
                .toList();
    }
    /** 先判预约存在及账号归属，再允许检查支付完整性或组装敏感快照。 */
    private void ensureOwner(Long userId, BookingQueryRecord record) {
        if (record == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!record.getAppointmentUserId().equals(userId)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    /** 采样秒精度服务端时间；列表只调用一次，避免同行响应出现不同截止判断。 */
    private LocalDateTime now() {
        return LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS);
    }
}
