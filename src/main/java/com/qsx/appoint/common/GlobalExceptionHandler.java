package com.qsx.appoint.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将 Controller 与业务层抛出的异常收敛为统一 JSON，避免每个接口重复编写错误处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getHttpStatus())
                .body(Result.failure(exception.getErrorCode()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> handleValidation(Exception exception) {
        return ResponseEntity.badRequest().body(Result.failure(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDuplicate(DataIntegrityViolationException exception) {
        return ResponseEntity.status(ErrorCode.EMAIL_EXISTS.getHttpStatus()).body(Result.failure(ErrorCode.EMAIL_EXISTS));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleDenied(AccessDeniedException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus()).body(Result.failure(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception) {
        log.error("未处理的服务端异常", exception);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getHttpStatus()).body(Result.failure(ErrorCode.INTERNAL_ERROR));
    }
}
