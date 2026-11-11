package com.qsx.appoint.support;

import com.qsx.appoint.catalog.mapper.DoctorMapper;
import java.util.UUID;

/** 用独立医生隔离测试场次，避免靠不断延长就诊日期绕开唯一键与三天窗口。 */
public final class BookingTestDoctors {
    private BookingTestDoctors() { }

    public static Long copyOf(DoctorMapper doctors, Long sourceId) {
        var doctor = doctors.selectById(sourceId);
        doctor.setId(null);
        doctor.setUserId(null);
        doctor.setDoctorNo("TEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        doctors.insert(doctor);
        return doctor.getId();
    }
}
