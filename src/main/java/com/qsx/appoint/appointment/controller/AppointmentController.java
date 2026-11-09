package com.qsx.appoint.appointment.controller;

import com.qsx.appoint.appointment.dto.*;
import com.qsx.appoint.appointment.service.AppointmentService;
import com.qsx.appoint.common.Result;
import com.qsx.appoint.coordination.application.BookingCoordinator;
import com.qsx.appoint.coordination.application.BookingLifecycleCoordinator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 已认证用户的预约HTTP入口：创建/取消交给coordination，详情/列表交给bookingquery。
 * <p>只负责身份提取和HTTP结果，不在Controller操作库存、支付或Mapper。
 */
@RestController @RequestMapping("/api/v1/appointments") @RequiredArgsConstructor
public class AppointmentController {
    private static final String REPLAY_MESSAGE = "该请求已创建预约，请查询详情获取当前状态";
    private final AppointmentService appointmentService;
    private final BookingCoordinator bookingCoordinator;
    private final BookingLifecycleCoordinator lifecycleCoordinator;

    /**
     * 创建预约并区分首次与幂等重放的HTTP响应。
     * @param authentication 安全过滤器设置的账号身份，principal为Long用户ID
     * @param idempotencyKey 客户端UUID请求键，网络重试沿用原值
     * @param request 经过Bean Validation的结构化请求，业务资格由用例判断
     * @return 首次201及完整创建结果；重放200及预约标识回执
     */
    @PostMapping
    public ResponseEntity<Result<AppointmentCreationResult>> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentCreationResult response = bookingCoordinator.create(
                (Long) authentication.getPrincipal(), idempotencyKey, request);
        if (response instanceof ReplayedAppointmentResponse) {
            return ResponseEntity.ok(Result.success(REPLAY_MESSAGE, response));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(response));
    }

    /**
     * 查询本人预约详情；不存在/归属错误由查询接口转业务异常。
     * @param id 预约单ID
     * @return HTTP200统一结果，包含当前状态和创建时的凭证快照
     */
    @GetMapping("/{id}") 
    public Result<AppointmentResponse> get(Authentication authentication, @PathVariable Long id) 
        {
             return Result.success(appointmentService.getOwned((Long) authentication.getPrincipal(), id)); 
        }

    /**
     * 查询当前账号的预约列表，不接受客户端指定其他账号ID。
     * @return HTTP200及列表，空账号数据返回空列表
     */
    @GetMapping("/my") 
    public Result<List<AppointmentResponse>> mine(Authentication authentication) 
        {
         return Result.success(appointmentService.listMine((Long) authentication.getPrincipal())); 
        }

    /**
     * 取消后重新读取当前详情，并用退款流程布尔值决定HTTP状态。
     * @param id 预约单ID
     * @return 无需退款或退款已完成为200；进入/仍处退款流程为202，不代表渠道已退回
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Result<AppointmentResponse>> cancel(Authentication authentication, @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        boolean refundPending = lifecycleCoordinator.cancelOwned(userId, id);
        AppointmentResponse response = appointmentService.getOwned(userId, id);
        Result<AppointmentResponse> result = refundPending
                ? Result.success("退款申请已受理", response)
                : Result.success(response);
        return ResponseEntity.status(refundPending ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result);
    }
}
