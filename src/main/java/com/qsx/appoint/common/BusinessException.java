package com.qsx.appoint.common;

import lombok.Getter;

/**
 * 业务层用此异常中断当前流程，由全局异常处理器转换为统一响应。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
