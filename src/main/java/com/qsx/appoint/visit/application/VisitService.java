package com.qsx.appoint.visit.application;

/** 维护同一预约的签到与接诊进度，不修改预约库存、快照或资金。 */
public interface VisitService {
    void checkIn(Long userId, Long appointmentId);
    void startVisit(Long doctorUserId, Long appointmentId);
    void completeVisit(Long doctorUserId, Long appointmentId);
}
