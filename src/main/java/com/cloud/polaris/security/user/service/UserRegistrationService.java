package com.cloud.polaris.security.user.service;

import com.cloud.polaris.common.exception.DuplicateResourceException;
import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.security.iam.domain.entity.TenantMembership;
import com.cloud.polaris.security.iam.repository.TenantMembershipRepository;
import com.cloud.polaris.security.user.api.RegisterUserRequest;
import com.cloud.polaris.security.user.api.UserResponse;
import com.cloud.polaris.security.user.domain.AppUser;
import com.cloud.polaris.security.user.repository.AppUserRepository;
import com.cloud.polaris.tenant.domain.Tenant;
import com.cloud.polaris.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final AppUserRepository appUserRepository;
    private final TenantRepository tenantRepository;
    private final TenantMembershipRepository tenantMembershipRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        Tenant tenant = tenantRepository.findById(request.tenantId()).orElseThrow(() -> new ResourceNotFoundException("tenant not found"));

        String normalizedUsername = request.username()
                .trim()
                .toLowerCase(Locale.ROOT);
        if(appUserRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateResourceException("username has already been registered");
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        AppUser user = AppUser.create(normalizedUsername, encodedPassword);

        appUserRepository.save(user);
        TenantMembership membership = TenantMembership.create(tenant, user);
        tenantMembershipRepository.save(membership);

        return UserResponse.from(user, tenant);
    }
}
