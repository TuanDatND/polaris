package com.cloud.polaris.instance.api;

import com.cloud.polaris.instance.service.InstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/instances")
@RequiredArgsConstructor
public class InstanceController {

    private final InstanceService instanceService;

    @GetMapping("/{id}")
    public InstanceResponse getInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id) {
        return instanceService.getInstance(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InstanceResponse createInstance(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody @Valid CreateInstanceRequest createInstanceRequest) {
        return instanceService.createInstance(tenantId, createInstanceRequest);
    }


}
