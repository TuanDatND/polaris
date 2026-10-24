package com.cloud.polaris.security.oauth2.repository;

import com.cloud.polaris.security.oauth2.domain.OAuth2AuthorizationConsentEntity;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface OAuth2AuthorizationConsentEntityRepository extends CrudRepository<OAuth2AuthorizationConsentEntity, String> {

    Optional<OAuth2AuthorizationConsentEntity> findByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);

    void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}
