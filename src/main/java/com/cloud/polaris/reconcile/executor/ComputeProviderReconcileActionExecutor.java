package com.cloud.polaris.reconcile.executor;

import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.CreateContainerRequest;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.reconcile.controller.PreparedReconcile;
import com.cloud.polaris.reconcile.domain.InstanceProvisioningSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ComputeProviderReconcileActionExecutor implements ReconcileActionExecutor {

    private final ComputeProvider computeProvider;

    @Override
    public ReconcileActionResult execute(PreparedReconcile prepared) {
        switch (prepared.decision()) {
            case CREATE -> {return create(prepared);}
            case START -> computeProvider.start(requireProviderResourceId(prepared));
            case STOP -> computeProvider.stop(requireProviderResourceId(prepared));
            case DELETE -> computeProvider.delete(requireProviderResourceId(prepared));

            case NOOP, WAIT, INVALID -> throw new IllegalArgumentException(
                    "Decision does not require provider I/O: "
                            + prepared.decision()
            );
        }
        return ReconcileActionResult.none();
    }

    private ReconcileActionResult create(PreparedReconcile prepared) {
        InstanceProvisioningSpec spec = prepared.provisioningSpec();
        if (spec == null || !prepared.instanceId().equals(spec.instanceId())) {
            throw new IllegalArgumentException("CREATE requires a matching provisioning spec");
        }

        // Observation and execution are separate: another attempt may have created it.
        ProviderResource resource = computeProvider.findByInstanceId(prepared.instanceId())
                .orElseGet(() -> computeProvider.createContainer(new CreateContainerRequest(
                        spec.instanceId(),
                        spec.tenantId(),
                        "polaris-" + spec.instanceId(),
                        spec.imageName(),
                        spec.cpuAllocated(),
                        spec.ramMb(),
                        Map.of(
                                "polaris.managed", "true",
                                "polaris.instance_id", spec.instanceId().toString(),
                                "polaris.tenant_id", spec.tenantId().toString()
                        )
                )));
        if (resource == null) {
            throw new IllegalStateException("Provider returned no resource for " + prepared.instanceId());
        }
        return ReconcileActionResult.created(resource.providerResourceId());
    }

    private String requireProviderResourceId(PreparedReconcile prepared) {
        if (prepared.providerResourceId() == null || prepared.providerResourceId().isBlank()) {
            throw new IllegalStateException(
                    "Provider resource id is required for "
                            + prepared.decision());
        }
        return prepared.providerResourceId();
    }
}
