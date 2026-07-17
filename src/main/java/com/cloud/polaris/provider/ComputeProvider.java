package com.cloud.polaris.provider;

import java.util.Optional;
import java.util.UUID;

public interface ComputeProvider {

    ProviderResource createContainer(CreateContainerRequest request);

    void start(String providerResourceId);

    void stop(String providerResourceId);

    void delete(String providerResourceId);

    Optional<ProviderResource> findByInstanceId(UUID instanceId);

}
