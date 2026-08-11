package com.hvac.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {

    @Test
    void usesJava21() {
        assertEquals(21, Runtime.version().feature());
    }
}
