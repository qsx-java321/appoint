package com.qsx.appoint.appointment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 新预约请求只提交医生、日期、场次类型和本人就诊人，不接收内部场次ID。
 * <p>字段注解校验结构，日期窗口、归属、启用状态与库存由业务事务复验。
 */
@Getter @Setter
public class CreateAppointmentRequest {
    /** 选定医生ID，不是创建快照或客户端指定医生编号。 */
    @NotNull
    private Long doctorId;

    /** 请求就诊日期，必须在实际占号时满足服务端D+1至D+7。 */
    @NotNull
    private java.time.LocalDate appointmentDate;

    /** 新四场次稳定名称，Bean Validation只验非空，业务层解析合法性。 */
    @NotBlank
    private String sessionType;

    /** 就诊人ID，不等同于账号用户。 */
    @NotNull
    private Long patientId;
}
