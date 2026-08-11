package com.hvac.simulator.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hvac.simulator.TestFixtures;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SimulatorTest {

    @Test
    void baselineProducesConfirmedTimeline() {
        var result = TestFixtures.runBaseline();

        assertEquals(10_080, result.steps().size());
        assertEquals(LocalDateTime.of(2024, 7, 1, 0, 0), result.steps().getFirst().timestamp());
        assertEquals(LocalDateTime.of(2024, 7, 7, 23, 59), result.steps().getLast().timestamp());
        assertEquals(138, result.steps().stream().filter(step -> step.totalPowerKw() > 0.0).count());
    }
}
