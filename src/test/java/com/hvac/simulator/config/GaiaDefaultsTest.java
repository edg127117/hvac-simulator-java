package com.hvac.simulator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GaiaDefaultsTest {

    @Test
    void gaiaDemoUsesConfirmedRange() {
        var config = SimulationConfig.gaiaDemo(42L);

        assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), config.start());
        assertEquals(LocalDateTime.of(2024, 7, 7, 23, 59), config.end());
        assertEquals(1, config.dtMinutes());
        assertEquals(10_080, config.expectedSteps());
    }

    @Test
    void gaiaHvacDefaultsPreserveRatedValues() {
        var parameters = HvacParameters.gaiaDefaults();

        assertEquals(1400.0, parameters.chillerRatedCapacityKw());
        assertEquals(6.0, parameters.chillerRatedCop());
        assertEquals(25.0, parameters.coolingSetpointC());
        assertEquals(2.0, parameters.deadbandC());
    }
}
