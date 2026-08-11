package com.hvac.simulator.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hvac.simulator.TestFixtures;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GaiaChartRendererTest {

    @TempDir
    Path tempDir;

    @Test
    void writesReadableThreePanelPng() throws Exception {
        Path target = tempDir.resolve("simulation_plot.png");

        new GaiaChartRenderer().write(TestFixtures.runBaseline(), target);
        var image = ImageIO.read(target.toFile());

        assertNotNull(image);
        assertEquals(1_200, image.getWidth());
        assertTrue(image.getHeight() >= 990 && image.getHeight() <= 1_010);
    }
}
