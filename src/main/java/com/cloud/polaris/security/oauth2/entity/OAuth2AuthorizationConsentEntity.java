package com.cloud.polaris.security.oauth2.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@RedisHash("oauth2_authorization_consent")
public class OAuth2AuthorizationConsentEntity {

    @Id
    private String id;

    @Indexed
    private String registeredClientId;

    @Indexed
    private String principalName;

    private Set<String> authorities;

    public static OAuth2AuthorizationConsentEntity create(
            String id,
            String registeredClientId,
            String principalName,
            Set<String> authorities
    ){
        OAuth2AuthorizationConsentEntity entity = new OAuth2AuthorizationConsentEntity();
        entity.id = id;
        entity.registeredClientId = registeredClientId;
        entity.principalName = principalName;
        entity.authorities = authorities;
        return entity;
    }
}
