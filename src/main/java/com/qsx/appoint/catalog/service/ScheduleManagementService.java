package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.dto.*;

public interface ScheduleManagementService {
    AdminScheduleResponse create(Long operatorUserId, CreateScheduleRequest request);
    AdminScheduleResponse update(Long scheduleId, UpdateScheduleRequest request);
    AdminScheduleResponse adjustInventory(Long operatorUserId, Long scheduleId,
                                          AdjustScheduleInventoryRequest request);
}
