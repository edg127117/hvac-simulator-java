package com.hvac.simulator.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hvac.simulator.config.SimulationConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BaselineWeatherSourceTest {

    @Test
    void baselineLoadsConfirmedWeatherColumns() throws Exception {
        var series = new BaselineWeatherSource("gaia-baseline/python-results.csv")
                .load(SimulationConfig.gaiaDemo(42L));

        assertEquals(10_080, series.points().size());
        assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), series.points().getFirst().timestamp());
        assertEquals(LocalDateTime.of(2024, 7, 7, 23, 59), series.points().getLast().timestamp());
    }
}
