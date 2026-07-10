package com.cloud.polaris.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateTenantRequest(@NotBlank String username, @Positive Integer quotaCpu, @Positive Integer quotaRamMb,
                                  @Positive Integer quotaInstanceCount
) {
}
