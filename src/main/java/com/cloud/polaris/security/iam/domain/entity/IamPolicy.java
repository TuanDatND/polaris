package com.cloud.polaris.security.iam.domain.entity;

import com.cloud.polaris.tenant.domain.Tenant;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class IamPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "name", nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document", nullable = false, columnDefinition = "jsonb")
    private JsonNode document;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(name = "managed", nullable = false)
    private boolean managed;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public static IamPolicy createGlobalPolicy(String name, JsonNode document) {
        IamPolicy policy = new IamPolicy();
        policy.name = name;
        policy.document = document;
        policy.policyVersion = 1;
        policy.managed = true;
        return policy;
    }

    public static IamPolicy createTenantPolicy(Tenant tenant, String name, JsonNode document) {
        IamPolicy policy = new IamPolicy();
        policy.tenant = tenant;
        policy.name = name;
        policy.document = document;
        policy.policyVersion = 1;
        policy.managed = false;
        return policy;
    }
}
