package com.cloud.polaris.security.iam.domain;

import com.cloud.polaris.tenant.domain.Tenant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_roles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class IamRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "managed", nullable = false)
    private boolean managed;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static IamRole createGlobalRole(String name,String description){
        IamRole role = new IamRole();
        role.name = name;
        role.description = description;
        return role;
    }

    public static IamRole createTenantRole(Tenant tenant, String name,String description){
        IamRole role = new IamRole();
        role.tenant = tenant;
        role.name = name;
        role.description = description;
        return role;
    }

}
