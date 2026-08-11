package com.hvac.simulator.output;

import com.hvac.simulator.simulation.Gaia11SimulationResult;
import com.hvac.simulator.simulation.Gaia11SimulationStep;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** 输出 Java 独立计算的 Gaia 1.1 30 字段 CSV。 */
public final class Gaia11CsvResultWriter {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void write(Gaia11SimulationResult result, Path target) throws IOException {
        Objects.requireNonNull(result, "Gaia 1.1 结果不能为空");
        Objects.requireNonNull(target, "CSV 目标路径不能为空");
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(absolute, StandardCharsets.UTF_8)) {
            writer.write(Gaia11SimulationStep.CSV_HEADER);
            writer.newLine();
            for (Gaia11SimulationStep step : result.steps()) {
                writer.write(toLine(step));
                writer.newLine();
            }
        }
    }

    private String toLine(Gaia11SimulationStep s) {
        return String.join(",", s.timestamp().format(FORMAT),
                d(s.outdoorC()), d(s.wetBulbC()), d(s.solarGlobalWPerM2()), d(s.roomC()),
                d(s.coolingLoadKw()), d(s.chillerPowerKw()), d(s.chilledWaterPumpPowerKw()),
                d(s.coolingWaterPumpPowerKw()), d(s.coolingTowerFanPowerKw()),
                d(s.terminalFanPowerKw()), d(s.totalPowerKw()), d(s.chillerPlr()), d(s.chillerCop()),
                d(s.chilledWaterFlowM3PerSecond()), d(s.coolingWaterFlowM3PerSecond()),
                d(s.chilledWaterSupplyC()), d(s.chilledWaterReturnC()), d(s.coolingWaterSupplyC()),
                d(s.coolingWaterReturnC()), d(s.pipeHeatGainKw()),
                d(s.chilledWaterFlowSensorM3PerSecond()), d(s.coolingWaterFlowSensorM3PerSecond()),
                d(s.chilledWaterSupplySensorC()), d(s.chilledWaterReturnSensorC()),
                d(s.coolingWaterSupplySensorC()), d(s.coolingWaterReturnSensorC()),
                d(s.chillerPowerTrueKw()), d(s.measuredCoolingKw()), d(s.measuredCop()));
    }

    private String d(double value) {
        return Double.toString(value);
    }
}
