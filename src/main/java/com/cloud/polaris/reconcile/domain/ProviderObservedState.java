package com.cloud.polaris.reconcile.domain;

public enum ProviderObservedState {
    CREATED,
    RUNNING,
    STOPPED,

    MISSING, //nghĩa là Docker xác nhận container không có.
    UNKNOWN  //nghĩa là chưa biết chắc, nên planner không được yêu cầu action nguy hiểm.
}
