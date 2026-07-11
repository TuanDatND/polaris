package com.cloud.polaris.common.exception;

public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }

    public static QuotaExceededException forRequest(int requestedCpu, int requestedRamMb, int availableCpu, int availableRamMb, int availableInstanceCount) {
        return new QuotaExceededException(
                "Quota exceeded: requested cpu=" + requestedCpu
                        + ", ramMb=" + requestedRamMb
                        + ". availableCpu=" + availableCpu
                        + ", availableRamMb=" + availableRamMb
                        + ", availableInstanceCount=" + availableInstanceCount
        );
    }
}
