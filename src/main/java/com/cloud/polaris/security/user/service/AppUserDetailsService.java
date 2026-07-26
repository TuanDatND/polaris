package com.cloud.polaris.security.user.service;

import com.cloud.polaris.security.user.domain.AppUser;
import com.cloud.polaris.security.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        String normalizedUsername = username
                .trim()
                .toLowerCase(Locale.ROOT);

        AppUser appUser = appUserRepository.findByUsername(normalizedUsername).orElseThrow(() -> new UsernameNotFoundException( "Invalid username or password"));

        return User.withUsername(appUser.getUsername())
                .password(appUser.getPasswordHash())
                .disabled(!appUser.isEnabled())
                .authorities(Collections.emptyList())
                .build();
    }
}
