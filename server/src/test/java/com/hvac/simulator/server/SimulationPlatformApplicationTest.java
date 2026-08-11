package com.hvac.simulator.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SimulationPlatformApplicationTest {
    @Test
    void applicationContextStartsWithMqttDisabledByDefault() {
    }
}
