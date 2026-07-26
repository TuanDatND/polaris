package com.cloud.polaris.security.user.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterUserRequest(
        @NotNull UUID tenantId,

        @Size(max = 100)
        @NotBlank String username,

        @Size(min = 6, max = 128)
        @NotBlank String password
) {
}
