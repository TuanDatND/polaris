package com.cloud.polaris.security.user.api;

import com.cloud.polaris.security.user.domain.AppUser;
import com.cloud.polaris.tenant.domain.Tenant;


public record UserResponse(
        String username,
        String tenantName
) {
    public static UserResponse from(AppUser user, Tenant tenant) {
        return new UserResponse(user.getUsername()
                ,tenant.getUsername()
        );
    }
}
