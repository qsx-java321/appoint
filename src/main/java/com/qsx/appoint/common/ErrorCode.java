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
    LOCAL_ENVIRONMENT_MISMATCH(1003, "运行环境已切换，请刷新页面", HttpStatus.CONFLICT),
    UNAUTHORIZED(2001, "请先登录或重新登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(2002, "没有权限执行此操作", HttpStatus.FORBIDDEN),
    LOGIN_FAILED(2003, "邮箱或密码错误", HttpStatus.UNAUTHORIZED),
    EMAIL_EXISTS(3001, "邮箱已注册", HttpStatus.CONFLICT),
    REGISTRATION_CODE_INVALID(3003, "验证码错误", HttpStatus.BAD_REQUEST),
    REGISTRATION_CODE_EXPIRED(3004, "验证码已过期，请重新获取", HttpStatus.BAD_REQUEST),
    SIGNUP_TICKET_INVALID(3005, "注册凭证无效，请重新验证邮箱", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(3002, "用户不存在", HttpStatus.NOT_FOUND),
    RESOURCE_NOT_FOUND(4001, "请求的资源不存在", HttpStatus.NOT_FOUND),
    SCHEDULE_UNAVAILABLE(4002, "该号源已不可预约", HttpStatus.CONFLICT),
    ORDER_STATE_ERROR(4003, "订单当前状态不允许该操作", HttpStatus.CONFLICT),
    PAYMENT_EXPIRED(4004, "支付期限已过", HttpStatus.CONFLICT),
    SCHEDULE_STARTED(4005, "该号源已开始就诊", HttpStatus.CONFLICT),
    PATIENT_DOCUMENT_EXISTS(4101, "该证件已登记为就诊人", HttpStatus.CONFLICT),
    PATIENT_DISABLED(4102, "该就诊人已停用", HttpStatus.CONFLICT),
    IDEMPOTENCY_CONFLICT(4103, "幂等键已用于其他预约请求", HttpStatus.CONFLICT),
    RETRYABLE_CONCURRENCY_ERROR(5001, "请求暂时无法处理，请使用原幂等键重试", HttpStatus.SERVICE_UNAVAILABLE),
    SCHEDULE_NOT_OPEN(4201, "排班尚未开放预约", HttpStatus.CONFLICT),
    SCHEDULE_CUTOFF_REACHED(4202, "已到预约截止时间", HttpStatus.CONFLICT),
    SCHEDULE_CLOSED(4203, "排班已关闭", HttpStatus.CONFLICT),
    DUPLICATE_ACTIVE_APPOINTMENT(4204, "该就诊人已持有本场有效预约", HttpStatus.CONFLICT),
    CANCELLATION_DEADLINE_REACHED(4205, "距开诊不足24小时，当前不可取消或退款", HttpStatus.CONFLICT),
    SCHEDULE_IDENTITY_LOCKED(4206, "排班已开放，医生、日期和时段不可修改", HttpStatus.CONFLICT),
    INVENTORY_REDUCTION_EXCEEDS_AVAILABLE(4207, "减少名额不能超过当前可用名额", HttpStatus.CONFLICT),
    DEPARTMENT_NAME_EXISTS(4401, "同一医院已存在该科室", HttpStatus.CONFLICT),
    CATALOG_RESOURCE_IN_USE(4402, "该资源仍被下级资源引用，不能删除", HttpStatus.CONFLICT),
    PAYMENT_CHANNEL_UNAVAILABLE(4301, "支付渠道暂不可用", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_SIGNATURE_INVALID(4302, "支付通知签名无效", HttpStatus.BAD_REQUEST),
    DOCTOR_ACCOUNT_ALREADY_BOUND(4501, "医生档案或账号已绑定，不支持换绑", HttpStatus.CONFLICT),
    VISIT_STATE_ERROR(4502, "当前日期或就诊进度不允许该操作", HttpStatus.CONFLICT),
    CHECK_IN_NOT_OPEN(4503, "尚未到签到开放时间", HttpStatus.CONFLICT),
    CHECK_IN_CLOSED(4504, "已超过预约场次签到截止时间", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
