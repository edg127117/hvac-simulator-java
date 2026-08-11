package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.ModeSignalValue;
import com.hvac.simulator.energy.runtime.PortValue;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record ModeSignalSpec(List<String> allowedModes) implements PortValueSpec {
    public ModeSignalSpec {
        Objects.requireNonNull(allowedModes, "允许模式集合不能为空");
        allowedModes = List.copyOf(allowedModes);
        if (allowedModes.isEmpty()
                || allowedModes.stream().anyMatch(mode -> mode == null || mode.isBlank())) {
            throw new IllegalArgumentException("允许模式集合不能为空且不能包含空白编码");
        }
        if (new HashSet<>(allowedModes).size() != allowedModes.size()) {
            throw new IllegalArgumentException("允许模式编码不能重复");
        }
    }

    @Override
    public Class<? extends PortValue> valueType() {
        return ModeSignalValue.class;
    }

    @Override
    public boolean supportsEnergyType(EnergyType energyType) {
        return energyType == EnergyType.CONTROL_SIGNAL;
    }
}
