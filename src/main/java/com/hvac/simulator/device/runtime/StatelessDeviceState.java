package com.hvac.simulator.device.runtime;

/** 无内部状态设备使用的显式状态，避免用 {@code null} 表示无状态。 */
public enum StatelessDeviceState implements DeviceState {
    INSTANCE
}
