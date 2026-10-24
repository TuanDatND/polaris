package com.cloud.polaris.reconcile.provider;

import com.cloud.polaris.reconcile.service.model.PreparedReconcile;
import com.cloud.polaris.reconcile.service.model.ReconcileActionResult;

public interface ReconcileActionExecutor {

    ReconcileActionResult execute(PreparedReconcile prepared);
}
