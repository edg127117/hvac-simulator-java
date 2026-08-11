package com.hvac.simulator.device.runtime;

import com.hvac.simulator.device.DeviceModuleKey;

/** 一个设备版本的单步计算入口；不负责拓扑传播、闭环迭代或守恒求解。 */
public interface DeviceRuntime<S extends DeviceState> {
    DeviceModuleKey moduleKey();

    DeviceStateDescriptor<S> stateDescriptor();

    CalculationOutcome<S> calculate(DeviceCalculationInput<S> input);
}
