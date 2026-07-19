package com.cloud.polaris.task.service;

import com.cloud.polaris.common.exception.ResourceNotFoundException;
import com.cloud.polaris.task.domain.ClaimedTask;
import com.cloud.polaris.task.domain.Task;
import com.cloud.polaris.task.repository.TaskRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TaskStateService {
    private final TaskRepository taskRepository;

    @Transactional
    public void retry(ClaimedTask claimedTask, Instant nextAvailableAt, String error) {
        Task task = taskRepository.findById(claimedTask.taskId()).orElseThrow(()->new ResourceNotFoundException("not found task "+claimedTask.taskId()));
        task.retry(nextAvailableAt, error);
    }
}
