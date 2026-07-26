package com.cloud.polaris.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain springSecurityFilterChain(HttpSecurity http, DaoAuthenticationProvider authenticationProvider) throws Exception {
        http.authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/oauth2/authorize",
                                "/oauth2/token",
                                "/oauth2/jwks",
                                "/.well-known/**",
                                "/login",
                                "/actuator/health")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractAuthenticationFilterConfigurer::permitAll)
                .logout(LogoutConfigurer::permitAll)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/auth/**"));
        return http.build();
    }
}
