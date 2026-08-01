package com.cloud.polaris.security.oauth2.service;

import com.cloud.polaris.security.oauth2.entity.OAuth2AuthorizationConsentEntity;
import com.cloud.polaris.security.oauth2.repository.OAuth2AuthorizationConsentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class RedisOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentEntityRepository oAuth2AuthorizationConsentEntityRepository;

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {

        String registeredClientId = authorizationConsent.getRegisteredClientId();

        String principalName = authorizationConsent.getPrincipalName();

        Set<GrantedAuthority> grantedAuthorities =
                Optional.ofNullable(authorizationConsent.getAuthorities())
                        .orElseGet(Set::of);

        Set<String> authorities = grantedAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        String id = registeredClientId + "::" + principalName;
        oAuth2AuthorizationConsentEntityRepository.save(
                OAuth2AuthorizationConsentEntity.create(id, registeredClientId, principalName, authorities));
    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        String registeredClientId = authorizationConsent.getRegisteredClientId();

        String principalName = authorizationConsent.getPrincipalName();
        oAuth2AuthorizationConsentEntityRepository.deleteByRegisteredClientIdAndPrincipalName(registeredClientId, principalName);
    }

    @Override
    public @Nullable OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        OAuth2AuthorizationConsentEntity entity = oAuth2AuthorizationConsentEntityRepository.findByRegisteredClientIdAndPrincipalName(registeredClientId, principalName).orElse(null);
        if (entity == null) {
            return null;
        }
        Set<String> storedAuthorities =
                Optional.ofNullable(entity.getAuthorities())
                        .orElseGet(Set::of);

        Set<GrantedAuthority> authorities = storedAuthorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return OAuth2AuthorizationConsent.withId(
                        entity.getRegisteredClientId(), entity.getPrincipalName())
                .authorities(
                        existingAuthorities ->
                                existingAuthorities.addAll(authorities))
                .build();
    }
}
