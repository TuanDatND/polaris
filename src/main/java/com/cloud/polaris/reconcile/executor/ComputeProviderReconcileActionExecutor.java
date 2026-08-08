package com.cloud.polaris.reconcile.executor;

import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.reconcile.controller.PreparedReconcile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ComputeProviderReconcileActionExecutor implements ReconcileActionExecutor {

    private final ComputeProvider computeProvider;

    @Override
    public void execute(PreparedReconcile prepared) {
        String providerResourceId = requireProviderResourceId(prepared);
        switch (prepared.decision()) {
            case START -> computeProvider.start(providerResourceId);
            case STOP -> computeProvider.stop(providerResourceId);
            case DELETE -> computeProvider.delete(providerResourceId);

            case NOOP, WAIT, INVALID -> throw new IllegalArgumentException(
                    "Decision does not require provider I/O: "
                            + prepared.decision()
            );
        }
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
