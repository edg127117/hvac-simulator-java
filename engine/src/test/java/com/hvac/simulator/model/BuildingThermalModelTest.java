package com.hvac.simulator.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hvac.simulator.config.BuildingEnvelope;
import com.hvac.simulator.config.InternalLoad;
import org.junit.jupiter.api.Test;

class BuildingThermalModelTest {

    private final BuildingThermalModel model = new BuildingThermalModel(
            BuildingEnvelope.gaiaDefaults(), InternalLoad.gaiaDefaults());

    @Test
    void weekdayOfficeHourUsesFullInternalLoad() {
        var gains = model.internalGains(9.0, true);

        assertEquals(305_000.0, gains.sensibleW(), 1e-9);
        assertEquals(55_000.0, gains.latentW(), 1e-9);
    }

    @Test
    void weekendOfficeHourUsesHalfInternalLoad() {
        var gains = model.internalGains(12.0, false);

        assertEquals(152_500.0, gains.sensibleW(), 1e-9);
        assertEquals(27_500.0, gains.latentW(), 1e-9);
    }

    @Test
    void stepUsesGaiaEulerBalance() {
        double actual = model.step(25.0, 10.0, 0.0, 0.0, true, 0.0, 60.0);

        assertEquals(19.7998, actual, 1e-9);
    }
}
