package com.cloud.polaris.tenant.domain;

import com.cloud.polaris.common.exception.QuotaExceededException;
import com.cloud.polaris.instance.domain.Instance;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    //Anemic Domain Model (Mô hình domain "thiếu máu" - Entity chỉ có getter/setter, service làm hết việc) sang Rich Domain Model (Mô hình domain "giàu có" - Entity tự mang trong mình logic của chính nó). Đây là xương sống của tư duy DDD (Domain-Driven Design).
    public static Tenant create(String username, Integer quotaCpu, Integer quotaRamMb, Integer quotaInstanceCount) {
        Tenant tenant = new Tenant();

        tenant.username = username;
        tenant.quotaCpu = (quotaCpu != null) ? quotaCpu : 10;
        tenant.quotaRamMb = (quotaRamMb != null) ? quotaRamMb : 8192;
        tenant.quotaInstanceCount = (quotaInstanceCount != null) ? quotaInstanceCount : 5;

        tenant.allocatedCpu = 0;
        tenant.allocatedRamMb = 0;
        tenant.allocatedInstanceCount = 0;
        return tenant;
    }

    public boolean canAllocate(int cpu, int ramMb) {
        return allocatedCpu + cpu <= quotaCpu
                && allocatedRamMb + ramMb <= quotaRamMb
                && allocatedInstanceCount + 1 <= quotaInstanceCount;
    }

    public void reserve(int cpu, int ramMb) {
        if (!canAllocate(cpu, ramMb)) {
            throw QuotaExceededException.forRequest(cpu, ramMb,
                    quotaCpu - allocatedCpu,
                    quotaRamMb - allocatedRamMb, quotaInstanceCount - allocatedInstanceCount);
        }

        allocatedCpu += cpu;
        allocatedRamMb += ramMb;
        allocatedInstanceCount += 1;
    }

    public void release(int cpu, int ramMb) {
        allocatedCpu -= cpu;
        allocatedRamMb -= ramMb;
        allocatedInstanceCount --;

        if(allocatedCpu < 0 || allocatedRamMb < 0 || allocatedInstanceCount < 0) {
            throw new IllegalStateException(
                    "Quota allocation cannot become negative. " +
                            "error code: Release tenant quota"
            );
        }
    }

}
