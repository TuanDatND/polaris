package com.cloud.polaris.security.iam.domain.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
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
public class RolePolicyId implements Serializable {

    @Column(name = "role_id", nullable = false)
    UUID roleId;

    @Column(name = "policy_id", nullable = false)
    UUID policyId;
}
