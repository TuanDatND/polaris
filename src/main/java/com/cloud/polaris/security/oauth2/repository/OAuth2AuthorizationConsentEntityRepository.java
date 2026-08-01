package com.cloud.polaris.security.oauth2.repository;

import com.cloud.polaris.security.oauth2.entity.OAuth2AuthorizationConsentEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface OAuth2AuthorizationConsentEntityRepository extends CrudRepository<OAuth2AuthorizationConsentEntity, String> {

    Optional<OAuth2AuthorizationConsentEntity> findByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);

    void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}
