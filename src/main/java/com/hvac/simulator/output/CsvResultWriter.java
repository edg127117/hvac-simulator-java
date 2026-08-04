package com.hvac.simulator.output;

import com.hvac.simulator.simulation.SimulationResult;
import com.hvac.simulator.simulation.SimulationStep;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/** 将仿真结果写成与 Gaia 兼容的 UTF-8、17 字段 CSV。 */
public final class CsvResultWriter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 先完整写入同目录临时文件，再替换正式文件，避免留下半写入结果。 */
    public void write(SimulationResult result, Path target) throws IOException {
        Objects.requireNonNull(result, "仿真结果不能为空");
        Objects.requireNonNull(target, "CSV 目标路径不能为空");
        Path absoluteTarget = target.toAbsolutePath();
        Path directory = absoluteTarget.getParent();
        Files.createDirectories(directory);
        Path temporary = directory.resolve(absoluteTarget.getFileName() + "." + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(SimulationStep.CSV_HEADER);
                writer.newLine();
                for (SimulationStep step : result.steps()) {
                    writer.write(toCsvLine(step));
                    writer.newLine();
                }
            }
            replaceAtomically(temporary, absoluteTarget);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private String toCsvLine(SimulationStep step) {
        return String.join(",",
                step.timestamp().format(TIMESTAMP_FORMAT),
                Double.toString(step.outdoorC()),
                Double.toString(step.wetBulbC()),
                Double.toString(step.solarGlobalWPerM2()),
                Double.toString(step.roomC()),
                Double.toString(step.coolingLoadKw()),
                Double.toString(step.chillerPowerKw()),
                Double.toString(step.chilledWaterPumpPowerKw()),
                Double.toString(step.coolingWaterPumpPowerKw()),
                Double.toString(step.coolingTowerFanPowerKw()),
                Double.toString(step.terminalFanPowerKw()),
                Double.toString(step.totalPowerKw()),
                Double.toString(step.chillerPlr()),
                Double.toString(step.chillerCop()),
                Double.toString(step.chilledWaterSupplyC()),
                Double.toString(step.coolingWaterSupplyC()),
                Double.toString(step.pipeHeatGainKw()));
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
