package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.BookingSettingsResponse;
import com.qsx.appoint.catalog.dto.UpdateBookingSettingsRequest;

public interface BookingSettingsService {
    BookingSettingsResponse get();

    BookingSettingsResponse update(UpdateBookingSettingsRequest request);
}
