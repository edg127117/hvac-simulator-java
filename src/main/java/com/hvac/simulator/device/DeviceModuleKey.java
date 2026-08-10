package com.hvac.simulator.device;

public record DeviceModuleKey(String deviceType, String moduleVersion) {
    public DeviceModuleKey {
        requireText(deviceType, "设备类型不能为空");
        requireText(moduleVersion, "设备模块版本不能为空");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
