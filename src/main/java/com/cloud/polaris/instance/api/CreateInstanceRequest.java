package com.cloud.polaris.instance.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateInstanceRequest(
        @NotBlank String name,
        @NotBlank String imageName,
        @NotNull
        @Positive
        Integer cpu,
        @NotNull
        @Positive
        Integer ramMb
) {
}
