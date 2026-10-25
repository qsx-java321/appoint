package com.qsx.appoint.catalog.service;

import com.qsx.appoint.catalog.entity.DoctorNumberAllocation;
import com.qsx.appoint.catalog.mapper.DoctorNumberAllocationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/** 为医生档案分配全局唯一、不会回收的对外编号。 */
@Component
@RequiredArgsConstructor
public class DoctorNumberGenerator {
    private final DoctorNumberAllocationMapper allocationMapper;

    @Transactional
    public String nextDoctorNo() {
        DoctorNumberAllocation allocation = new DoctorNumberAllocation();
        allocation.setAllocationToken(UUID.randomUUID().toString());
        allocationMapper.insert(allocation);
        return String.format(Locale.ROOT, "DOC%06d", allocation.getId());
    }
}
