package com.hvac.simulator.measurement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 读取 Python 冻结的标准化抽样，Java 仍独立应用当前测量参数。 */
public final class FrozenRandomDrawSource implements RandomDrawSource {
    private static final String HEADER = String.join(",",
            "datetime", "power_uniform_unit", "chw_flow_normal", "cw_flow_normal",
            "T_chw_supply_normal", "T_chw_return_normal", "T_cw_supply_normal",
            "T_cw_return_normal");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final List<Entry> entries;

    public FrozenRandomDrawSource(String resourceName) throws IOException {
        Objects.requireNonNull(resourceName, "随机基准资源名不能为空");
        var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (stream == null) {
            throw new IOException("找不到随机基准资源：" + resourceName);
        }
        var loaded = new ArrayList<Entry>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("随机基准 CSV 表头不匹配：" + resourceName);
            }
            String line;
            int fileLine = 1;
            while ((line = reader.readLine()) != null) {
                fileLine++;
                String[] fields = line.split(",", -1);
                if (fields.length != 8) {
                    throw new IOException("随机基准 CSV 字段数错误，行=" + fileLine);
                }
                try {
                    loaded.add(new Entry(
                            LocalDateTime.parse(fields[0], FORMAT),
                            new Gaia11RandomDraws(
                                    fields[1].isEmpty() ? null : Double.valueOf(fields[1]),
                                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3]),
                                    Double.parseDouble(fields[4]), Double.parseDouble(fields[5]),
                                    Double.parseDouble(fields[6]), Double.parseDouble(fields[7]))));
                } catch (RuntimeException exception) {
                    throw new IOException("随机基准 CSV 数值错误，行=" + fileLine, exception);
                }
            }
        }
        entries = List.copyOf(loaded);
    }

    @Override
    public Gaia11RandomDraws draws(int step, LocalDateTime timestamp, boolean powerMeterActive) {
        if (step < 0 || step >= entries.size()) {
            throw new IllegalArgumentException("随机基准步号越界：" + step);
        }
        Entry entry = entries.get(step);
        if (!entry.timestamp().equals(timestamp)) {
            throw new IllegalArgumentException("随机基准时间不一致，步号=" + step);
        }
        boolean hasPowerDraw = entry.draws().powerUniformUnit() != null;
        if (hasPowerDraw != powerMeterActive) {
            throw new IllegalArgumentException("电表随机消费顺序不一致，步号=" + step);
        }
        return entry.draws();
    }

    private record Entry(LocalDateTime timestamp, Gaia11RandomDraws draws) {}
}
