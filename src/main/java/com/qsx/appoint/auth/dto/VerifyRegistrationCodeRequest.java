package com.qsx.appoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRegistrationCodeRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {}
