package com.qsx.appoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 注册接口输入，只包含当前阶段确实需要的身份信息。 */
@Getter
@Setter
public class RegisterRequest {
    @NotBlank
    private String signupTicket;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
