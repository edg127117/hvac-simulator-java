package com.hvac.simulator.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.config.SimulationConfig;
import com.hvac.simulator.config.WeatherParameters;
import org.junit.jupiter.api.Test;

class SyntheticWeatherGeneratorTest {

    @Test
    void syntheticWeatherRepeatsWithSameSeed() throws Exception {
        var source = new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults());

        assertEquals(
                source.load(SimulationConfig.gaiaDemo(42L)),
                source.load(SimulationConfig.gaiaDemo(42L)));
        assertNotEquals(
                source.load(SimulationConfig.gaiaDemo(42L)),
                source.load(SimulationConfig.gaiaDemo(43L)));
    }

    @Test
    void syntheticSolarRadiationIsNeverNegative() throws Exception {
        var series = new SyntheticWeatherGenerator(WeatherParameters.gaiaDefaults())
                .load(SimulationConfig.gaiaDemo(42L));

        assertTrue(series.points().stream().allMatch(point -> point.solarGlobalWPerM2() >= 0.0));
    }
}
