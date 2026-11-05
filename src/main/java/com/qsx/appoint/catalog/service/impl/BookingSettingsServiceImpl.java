package com.qsx.appoint.catalog.service.impl;

import com.qsx.appoint.catalog.dto.BookingSettingsResponse;
import com.qsx.appoint.catalog.dto.UpdateBookingSettingsRequest;
import com.qsx.appoint.catalog.entity.BookingSettings;
import com.qsx.appoint.catalog.mapper.BookingSettingsMapper;
import com.qsx.appoint.catalog.service.BookingSettingsService;
import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class BookingSettingsServiceImpl implements BookingSettingsService {
    private final BookingSettingsMapper mapper;
    private final Clock appointmentClock;

    @Override
    @Transactional(readOnly = true)
    public BookingSettingsResponse get() {
        return response(requireSettings(mapper.selectById((short) 1)));
    }

    @Override
    @Transactional
    public BookingSettingsResponse update(UpdateBookingSettingsRequest request) {
        BookingSettings settings = requireSettings(mapper.selectForUpdate());
        settings.setCapacityPerSession(request.capacityPerSession());
        settings.setRegistrationFee(request.registrationFee().setScale(2, RoundingMode.UNNECESSARY));
        settings.setCurrency("CNY");
        settings.setUpdatedAt(LocalDateTime.now(appointmentClock).truncatedTo(ChronoUnit.SECONDS));
        if (mapper.updateById(settings) != 1) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        return response(settings);
    }

    private BookingSettings requireSettings(BookingSettings settings) {
        if (settings == null) throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        return settings;
    }

    private BookingSettingsResponse response(BookingSettings settings) {
        return new BookingSettingsResponse(settings.getCapacityPerSession(), settings.getRegistrationFee(),
                settings.getCurrency(), settings.getUpdatedAt());
    }
}
