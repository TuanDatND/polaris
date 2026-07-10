package com.cloud.polaris.tenant.domain;

import com.cloud.polaris.instance.domain.Instance;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name = "tenants")
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "quota_cpu", nullable = false)
    private Integer quotaCpu;

    @Column(name = "quota_ram_mb", nullable = false)
    private Integer quotaRamMb;

    @Column(name = "quota_instance_count", nullable = false)
    private Integer quotaInstanceCount;

    @Column(name = "allocated_cpu", nullable = false)
    private Integer allocatedCpu;

    @Column(name = "allocated_ram_mb", nullable = false)
    private Integer allocatedRamMb;

    @Column(name = "allocated_instance_count", nullable = false)
    private Integer allocatedInstanceCount;

    @Version
    private Long version;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "tenant", fetch = FetchType.LAZY)
    private List<Instance> instances;
}
