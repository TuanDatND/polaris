package com.cloud.polaris.security.iam.domain.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class UserRoleId {

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "role_id", nullable = false)
    UUID roleId;
}
