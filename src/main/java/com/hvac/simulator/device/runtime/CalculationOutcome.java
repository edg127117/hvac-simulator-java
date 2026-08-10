package com.hvac.simulator.device.runtime;

/** 单步设备计算的显式成功或失败结果。 */
public sealed interface CalculationOutcome<S extends DeviceState>
        permits CalculationSuccess, CalculationFailure {
}
