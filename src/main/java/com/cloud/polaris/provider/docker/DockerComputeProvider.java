package com.cloud.polaris.provider.docker;

import com.cloud.polaris.provider.ComputeProvider;
import com.cloud.polaris.provider.CreateContainerRequest;
import com.cloud.polaris.provider.ProviderResource;
import com.cloud.polaris.provider.ProviderResourceStatus;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DockerComputeProvider implements ComputeProvider {

    private final DockerClient dockerClient;

    @Override
    public ProviderResource createContainer(CreateContainerRequest request) {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(request.ramMb() * 1024L)
                .withCpuCount((long) request.cpuAllocated());

        CreateContainerResponse response = dockerClient
                .createContainerCmd(request.imageName())
                .withName(request.name())
                .withLabels(request.labels())
                .withHostConfig(hostConfig)
                .exec();

        return new ProviderResource(response.getId(), request.name(), ProviderResourceStatus.CREATED);
    }

    @Override
    public void start(String providerResourceId) {
        try{
            InspectContainerResponse container = dockerClient.inspectContainerCmd(providerResourceId).exec();

            String status = container.getState().getStatus();
            if ("running".equalsIgnoreCase(status)) {
                return;
            }
            dockerClient.startContainerCmd(providerResourceId).exec();
        }catch (NotModifiedException e){
            // Container đã running
        }
    }

    @Override
    public void stop(String providerResourceId) {
        dockerClient.stopContainerCmd(providerResourceId).exec();
    }

    @Override
    public void delete(String providerResourceId) {
        dockerClient.removeContainerCmd(providerResourceId).withForce(true).exec();
    }

    @Override
    public Optional<ProviderResource> findByInstanceId(UUID instanceId) {
        List<Container> containers = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of(
                        "polaris.managed", "true",
                        "polaris.instance_id", instanceId.toString()
                ))
                .exec();

        if (containers.isEmpty()) {
            return Optional.empty();
        }

        if (containers.size() > 1) {
            throw new IllegalStateException("Multiple Docker containers found for instance: " + instanceId + " please inform admin");
        }

        Container container = containers.getFirst();
        String name = null;
        if (container.getNames() != null && container.getNames().length > 0) {
            name = container.getNames()[0];

            // Docker thường trả name dạng "/polaris-instance-123"
            if (name.startsWith("/")) {
                name = name.substring(1);
            }
        }
        ProviderResourceStatus status =
                switch (container.getState().toLowerCase()) {
                    case "running" -> ProviderResourceStatus.RUNNING;
                    case "created" -> ProviderResourceStatus.CREATED;
                    case "exited", "dead" -> ProviderResourceStatus.STOPPED;
                    default -> ProviderResourceStatus.UNKNOWN;
                };

        return Optional.of(new ProviderResource(container.getId(), name, status));
    }
}
