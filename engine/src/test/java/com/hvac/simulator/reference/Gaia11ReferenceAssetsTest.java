package com.hvac.simulator.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class Gaia11ReferenceAssetsTest {
    private static final Path REPOSITORY_ROOT = locateRepositoryRoot();

    @Test
    void preservesOriginalSourceBytes() throws Exception {
        Path source = REPOSITORY_ROOT.resolve("reference/gaia-1.1/Gaia1.1.py");

        assertEquals(
                "4E71C1FFECBEA97057F9ED34153441EF49B2F655DFF36651FE4C9214A180C370",
                sha256(source));
    }

    @Test
    void freezesCompleteBaselineAssetsOutsideProductionResults() throws Exception {
        Path weather = REPOSITORY_ROOT.resolve(
                "engine/src/main/resources/gaia-baseline/gaia-1.1/python-weather.csv");
        Path random = REPOSITORY_ROOT.resolve(
                "engine/src/main/resources/gaia-baseline/gaia-1.1/python-random-draws.csv");
        Path results = REPOSITORY_ROOT.resolve(
                "engine/src/test/resources/gaia-baseline/gaia-1.1/python-results.csv");
        Path plot = REPOSITORY_ROOT.resolve("reference/gaia-1.1/python-reference-plot.png");

        assertCsv(weather, 4, 10_080, "2024-07-01 00:00:00", "2024-07-07 23:59:00");
        assertCsv(random, 8, 10_080, "2024-07-01 00:00:00", "2024-07-07 23:59:00");
        assertCsv(results, 30, 10_080, "2024-07-01 00:00:00", "2024-07-07 23:59:00");
        assertTrue(Files.size(plot) > 10_000, "五联参考图不能为空或异常过小");
        assertFalse(Files.exists(REPOSITORY_ROOT.resolve(
                "engine/src/main/resources/gaia-baseline/gaia-1.1/python-results.csv")));
    }

    private static void assertCsv(
            Path path, int columns, long dataRows, String firstTimestamp, String lastTimestamp)
            throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String header = reader.readLine();
            assertEquals(columns, header.split(",", -1).length, path + " 字段数");
            String first = reader.readLine();
            String last = first;
            long rows = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                last = line;
                rows++;
            }
            assertEquals(dataRows, rows, path + " 数据行数");
            assertTrue(first.startsWith(firstTimestamp + ","), path + " 首时间戳");
            assertTrue(last.startsWith(lastTimestamp + ","), path + " 末时间戳");
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("reference/gaia-1.1/Gaia1.1.py"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("无法定位仓库根目录");
        }
        return current;
    }
}
