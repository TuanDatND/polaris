package com.cloud.polaris.security.iam.domain.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

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
