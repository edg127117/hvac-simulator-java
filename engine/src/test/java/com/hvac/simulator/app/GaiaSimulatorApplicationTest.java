package com.hvac.simulator.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GaiaSimulatorApplicationTest {

    @TempDir
    Path tempDir;

    @Test
    void baselineRunCreatesBothArtifacts() throws Exception {
        int exitCode = new GaiaSimulatorApplication().run(new String[] {
            "--weather=baseline", "--output=" + tempDir
        });

        assertEquals(0, exitCode);
        assertTrue(Files.size(tempDir.resolve("hvac_simulation_results.csv")) > 0);
        assertTrue(Files.size(tempDir.resolve("simulation_plot.png")) > 0);
    }

    @Test
    void gaia11RunCreatesThirtyFieldCsvAndFivePanelPlot() throws Exception {
        Path output = tempDir.resolve("gaia11");

        int exitCode = new GaiaSimulatorApplication().run(new String[] {
            "--model=gaia-1.1", "--weather=baseline", "--output=" + output
        });

        assertEquals(0, exitCode);
        String header = Files.readAllLines(output.resolve("hvac_simulation_results.csv")).getFirst();
        assertEquals(30, header.split(",", -1).length);
        var image = javax.imageio.ImageIO.read(output.resolve("simulation_plot.png").toFile());
        assertEquals(1_400, image.getWidth());
        assertTrue(image.getHeight() >= 1_390 && image.getHeight() <= 1_410);
    }

    @Test
    void rejectsUnknownArguments() {
        var application = new GaiaSimulatorApplication();

        assertThrows(IllegalArgumentException.class,
                () -> application.run(new String[] {"--unknown=value"}));
        assertThrows(IllegalArgumentException.class,
                () -> application.run(new String[] {"--weather=invalid"}));
        assertThrows(IllegalArgumentException.class,
                () -> application.run(new String[] {"--seed=not-a-number"}));
        assertThrows(IllegalArgumentException.class,
                () -> application.run(new String[] {"--model=gaia-2.0"}));
    }
}
