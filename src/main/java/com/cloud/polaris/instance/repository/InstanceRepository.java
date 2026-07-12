package com.cloud.polaris.instance.repository;

import com.cloud.polaris.instance.domain.CurrentState;
import com.cloud.polaris.instance.domain.Instance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstanceRepository extends JpaRepository<Instance, UUID> {

    boolean existsByTenant_IdAndNameAndCurrentStateNot(UUID tenantId,
                                                       String name,
                                                       CurrentState currentState);

    Optional<Instance> findByIdAndTenant_Id(UUID id, UUID tenantId);
}

