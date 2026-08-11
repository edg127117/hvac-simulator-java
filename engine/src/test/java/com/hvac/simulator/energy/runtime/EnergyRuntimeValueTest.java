package com.hvac.simulator.energy.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EnergyRuntimeValueTest {

    @Test
    void fluidPropertiesRequireFinitePositiveValues() {
        var properties = new FluidProperties(FluidMedium.WATER, 998.2, 4_180.0);

        assertEquals(FluidMedium.WATER, properties.medium());
        assertThrows(IllegalArgumentException.class,
                () -> new FluidProperties(FluidMedium.WATER, Double.NaN, 4_180.0));
        assertThrows(IllegalArgumentException.class,
                () -> new FluidProperties(FluidMedium.WATER, 998.2, 0.0));
    }

    @Test
    void pressureUsesKnownOrExplicitUnavailableState() {
        PressureValue known = new KnownPressure(250_000.0);
        PressureValue unavailable = UnavailablePressure.INSTANCE;

        assertEquals(250_000.0, ((KnownPressure) known).pascals());
        assertEquals(UnavailablePressure.INSTANCE, unavailable);
        assertThrows(IllegalArgumentException.class, () -> new KnownPressure(-1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new KnownPressure(Double.POSITIVE_INFINITY));
    }

    @Test
    void mediumCodeRejectsBlankText() {
        assertThrows(IllegalArgumentException.class, () -> new FluidMedium(" "));
    }
}
