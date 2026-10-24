package com.cloud.polaris.instance.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.instance.api.InstanceReconciliationResponse;
import com.cloud.polaris.instance.api.InstanceResponse;
import com.cloud.polaris.instance.domain.Instance;
import com.cloud.polaris.instance.repository.InstanceRepository;
import com.cloud.polaris.reconcile.domain.ReconcileRequest;
import com.cloud.polaris.reconcile.repository.ReconcileRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InstanceQueryService {
    private final InstanceRepository instanceRepository;
    private final ReconcileRequestRepository reconcileRequestRepository;

    @Transactional(readOnly = true)
    public InstanceResponse getInstance(UUID tenantId, UUID instanceId) {
        Instance instance = instanceRepository.findByIdAndTenant_Id(instanceId, tenantId).orElseThrow(() -> new ResourceNotFoundException("Instance not found: " + instanceId));

        return InstanceResponse.from(instance);
    }

    @Transactional(readOnly = true)
    public InstanceReconciliationResponse getReconciliation(
            UUID tenantId,
            UUID instanceId
    ) {
        Instance instance = instanceRepository
                .findByIdAndTenant_Id(instanceId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Instance not found: " + instanceId
                ));

        ReconcileRequest request = reconcileRequestRepository
                .findById(instance.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reconciliation state not found: " + instanceId
                ));

        return InstanceReconciliationResponse.from(request);
    }
}
