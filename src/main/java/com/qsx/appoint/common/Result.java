package com.qsx.appoint.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 所有 HTTP 接口统一使用的响应结构。
 * HTTP 状态码表达协议语义，code 用于让客户端按业务类型处理错误。
 */
@Getter
@AllArgsConstructor
public class Result<T> {

    private final int code;
    private final String message;
    private final T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }

    public static <T> Result<T> failure(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
