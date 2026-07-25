package com.cloud.polaris.security.iam.domain.composite;

import com.cloud.polaris.security.iam.domain.entity.IamPolicy;
import com.cloud.polaris.security.iam.domain.entity.IamRole;
import com.cloud.polaris.security.iam.domain.id.RolePolicyId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "iam_role_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IamRolePolicy {

    @EmbeddedId
    private RolePolicyId id;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private IamRole role;

    @MapsId("policyId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private IamPolicy policy;

}
