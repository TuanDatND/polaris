package com.cloud.polaris.instance.api;

import com.cloud.polaris.instance.service.InstanceCommandService;
import com.cloud.polaris.instance.service.InstanceQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceQueryService instanceQueryService;
    private final InstanceCommandService instanceCommandService;

    @GetMapping("/{id}")
    public InstanceResponse getInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return instanceQueryService.getInstance(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InstanceResponse createInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody @Valid CreateInstanceRequest createInstanceRequest) {
        return instanceCommandService.createInstance(tenantId, createInstanceRequest);
    }

    @PostMapping("/{instanceId}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InstanceResponse stopInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID instanceId) {
        return instanceCommandService.stopInstance(tenantId,instanceId);
    }
}
