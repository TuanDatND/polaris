package com.cloud.polaris.reconcile.executor;

import com.cloud.polaris.reconcile.controller.PreparedReconcile;

public interface ReconcileActionExecutor {

    ReconcileActionResult execute(PreparedReconcile prepared);
}
