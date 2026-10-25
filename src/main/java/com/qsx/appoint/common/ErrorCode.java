package com.qsx.appoint.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 错误码按模块分段，避免后续业务增加时发生含义冲突。
 */
@Getter
public enum ErrorCode {
    VALIDATION_ERROR(1001, "请求参数不合法", HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(1002, "服务暂时不可用", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED(2001, "请先登录或重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(2002, "没有权限执行此操作", HttpStatus.FORBIDDEN),
    LOGIN_FAILED(2003, "邮箱或密码错误", HttpStatus.UNAUTHORIZED),
    EMAIL_EXISTS(3001, "邮箱已注册", HttpStatus.CONFLICT),
    USER_NOT_FOUND(3002, "用户不存在", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(4001, "请求的资源不存在", HttpStatus.NOT_FOUND),
    SCHEDULE_UNAVAILABLE(4002, "该号源已不可预约", HttpStatus.CONFLICT),
    ORDER_STATE_ERROR(4003, "订单当前状态不允许该操作", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
