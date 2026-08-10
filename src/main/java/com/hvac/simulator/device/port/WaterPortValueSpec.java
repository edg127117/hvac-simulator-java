package com.hvac.simulator.device.port;

import com.hvac.simulator.energy.EnergyType;
import com.hvac.simulator.energy.runtime.FluidMedium;
import com.hvac.simulator.energy.runtime.PortValue;
import com.hvac.simulator.energy.runtime.WaterPortValue;
import java.util.Objects;

public record WaterPortValueSpec(FluidMedium medium) implements PortValueSpec {
    public WaterPortValueSpec {
        Objects.requireNonNull(medium, "水介质编码不能为空");
    }

    @Override
    public Class<? extends PortValue> valueType() {
        return WaterPortValue.class;
    }

    @Override
    public boolean supportsEnergyType(EnergyType energyType) {
        return energyType != null && energyType.isWater();
    }
}
