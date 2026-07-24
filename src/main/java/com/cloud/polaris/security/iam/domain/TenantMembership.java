package com.cloud.polaris.security.iam.domain;

import com.cloud.polaris.security.user.domain.AppUser;
import com.cloud.polaris.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenant_memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantMembership {

    //Composite key
    @EmbeddedId
    private MembershipId id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;
}
