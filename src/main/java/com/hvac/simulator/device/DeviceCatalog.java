package com.hvac.simulator.device;

import java.util.Optional;

@FunctionalInterface
public interface DeviceCatalog {
    Optional<DeviceDefinition> find(DeviceModuleKey key);
}
