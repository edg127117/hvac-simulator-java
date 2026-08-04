package com.hvac.simulator.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hvac.simulator.TestFixtures;
import com.hvac.simulator.simulation.SimulationStep;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvResultWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesGaiaHeaderAndAllRows() throws Exception {
        Path target = tempDir.resolve("hvac_simulation_results.csv");

        new CsvResultWriter().write(TestFixtures.runBaseline(), target);
        var lines = Files.readAllLines(target, StandardCharsets.UTF_8);

        assertEquals(SimulationStep.CSV_HEADER, lines.getFirst());
        assertEquals(10_081, lines.size());
    }
}
