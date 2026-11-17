package com.qsx.appoint.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SendRegistrationCodeRequest(@NotBlank @Email String email) {}
