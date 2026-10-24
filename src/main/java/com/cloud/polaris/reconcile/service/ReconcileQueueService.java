package com.cloud.polaris.reconcile.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.reconcile.domain.ReconcileRequest;
import com.cloud.polaris.reconcile.domain.ReconcileRetryPolicy;
import com.cloud.polaris.reconcile.repository.ReconcileRequestRepository;
import com.cloud.polaris.reconcile.service.model.ClaimedReconcileRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconcileQueueService {

    private final ReconcileRequestRepository reconcileRequestRepository;
    private final ReconcileRetryPolicy reconcileRetryPolicy;

    /*wake
        Chưa có request → tạo một row READY.
        Đã có request READY → giữ một row duy nhất, cập nhật requested_generation lớn nhất.
        Đang RUNNING → không cướp claim/lease của worker hiện tại; chỉ ghi nhận generation mới để worker biết cần reconcile tiếp sau khi xong.
        Nếu request đang BLOCKED hoặc đã được schedule resync → đưa nó về READY để xử lý sớm.
    */
    @Transactional
    public void wake(UUID instanceId, long generation) {
        Instant now = Instant.now();
        reconcileRequestRepository.wake(instanceId, generation, now);
    }

    @Transactional
    public List<ClaimedReconcileRequest> claimReady(int limit, String workerId, Duration leaseDuration) {
        Instant now = Instant.now();
        List<ReconcileRequest> claimedRequests = reconcileRequestRepository.findReadyForUpdate(now, limit);

        for (ReconcileRequest request : claimedRequests) {
            request.claim(workerId, UUID.randomUUID(), now.plus(leaseDuration));
        }

        return claimedRequests.stream().map(request -> new ClaimedReconcileRequest(
                        request.getInstanceId(),
                        request.getRequestedGeneration(),
                        request.getClaimToken()
                ))
                .toList();
    }

    @Transactional
    public int recoverExpiredLeases(int limit) {
        Instant now = Instant.now();

        List<ReconcileRequest> expired = reconcileRequestRepository.findExpiredLeasesForUpdate(now, limit);

        expired.forEach(request -> request.recoverExpiredLease(now));
        return expired.size();
    }

    @Transactional
    public void requeue(ClaimedReconcileRequest claimed, String error) {
        ReconcileRequest request = lockOwned(claimed);
        Instant now = Instant.now();
        if (request.getRequestedGeneration() > claimed.requestedGeneration()) {
            request.scheduleResync(now);
            return;
        }

        Duration delay = reconcileRetryPolicy.delayAfterFailures(
                request.getFailureCount(),
                ThreadLocalRandom.current().nextDouble()
        );
        request.requeue(now.plus(delay), error);
    }

    @Transactional
    public void scheduleResync(ClaimedReconcileRequest claimed, Instant nextAvailableAt) {
        ReconcileRequest request = lockOwned(claimed);
        request.scheduleResync(nextAvailableAt);
    }

    @Transactional
    public void block(ClaimedReconcileRequest claimed, String error) {
        ReconcileRequest request = lockOwned(claimed);
        request.block(error);
    }

    private ReconcileRequest lockOwned(ClaimedReconcileRequest claimed) {
        ReconcileRequest request = reconcileRequestRepository.findByInstanceIdForUpdate(claimed.instanceId()).orElseThrow(() -> new ResourceNotFoundException("Reconcile request not found: " + claimed.instanceId()));
        request.assertClaimToken(claimed.claimToken());
        return request;
    }
}
