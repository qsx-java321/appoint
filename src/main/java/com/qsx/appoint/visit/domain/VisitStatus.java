package com.qsx.appoint.visit.domain;

import com.qsx.appoint.common.BusinessException;
import com.qsx.appoint.common.ErrorCode;

/** NOT_CHECKED_IN 仅用于查询，其他三个值存储在就诊记录中。 */
public enum VisitStatus {
    NOT_CHECKED_IN, WAITING, IN_PROGRESS, COMPLETED;

    public static VisitStatus fromStored(String value) {
        try {
            VisitStatus status = valueOf(value);
            if (status == NOT_CHECKED_IN) throw new IllegalArgumentException();
            return status;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
