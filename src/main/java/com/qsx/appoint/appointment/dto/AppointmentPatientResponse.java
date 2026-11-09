package com.qsx.appoint.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 创建时就诊人凭证的对外快照，证件号通过脱敏字段展示。 */
@Getter
@AllArgsConstructor
public class AppointmentPatientResponse {
    /** 就诊人ID，不是账号用户ID。 */
    private final Long id;
    /** 创建时就诊人姓名快照。 */
    private final String name;
    /** 规范化证件类型，参与同场次跨账号证件唯一约束。 */
    private final String documentType;
    /** 已脱敏证件号，只展示允许公开的部分。 */
    private final String documentNoMasked;
    /** 创建时联系电话快照。 */
    private final String contactPhone;
    /** 创建时联系邮箱快照。 */
    private final String contactEmail;
}
