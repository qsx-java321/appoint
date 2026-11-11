package com.qsx.appoint.catalog.dto;

import jakarta.validation.constraints.NotNull;

public record BindDoctorAccountRequest(@NotNull Long userId) {}
