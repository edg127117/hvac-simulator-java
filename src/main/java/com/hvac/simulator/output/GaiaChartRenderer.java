package com.hvac.simulator.output;

import com.hvac.simulator.simulation.SimulationResult;
import com.hvac.simulator.simulation.SimulationStep;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.BitmapEncoder.BitmapFormat;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler.LegendPosition;
import org.knowm.xchart.style.markers.Marker;
import org.knowm.xchart.style.markers.SeriesMarkers;

/** 输出与 Python 参考图语义一致的温度、负荷/功率和 COP 三联 PNG。 */
public final class GaiaChartRenderer {

    private static final int WIDTH = 1_200;
    private static final int PANEL_HEIGHT = 333;
    private static final Color BLUE = new Color(31, 119, 180);
    private static final Color ORANGE = new Color(255, 127, 14);
    /** 使用同目录临时文件完成三图合成后替换目标，避免中断时留下损坏 PNG。 */
    public void write(SimulationResult result, Path target) throws IOException {
        Objects.requireNonNull(result, "仿真结果不能为空");
        Objects.requireNonNull(target, "PNG 目标路径不能为空");
        Font chineseFont = chooseChineseFont();
        List<Date> timestamps = result.steps().stream()
                .map(step -> Date.from(step.timestamp().atZone(ZoneId.systemDefault()).toInstant()))
                .toList();

        XYChart temperature = baseChart("温度 (℃)", "", chineseFont);
        addLine(temperature, "室温", timestamps, values(result, SimulationStep::roomC), BLUE);
        addLine(temperature, "室外温度", timestamps, values(result, SimulationStep::outdoorC),
                new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 178));

        XYChart power = baseChart("功率 (kW)", "", chineseFont);
        addLine(power, "冷负荷", timestamps, values(result, SimulationStep::coolingLoadKw), BLUE);
        addLine(power, "系统总功率", timestamps, values(result, SimulationStep::totalPowerKw), ORANGE);

        XYChart cop = baseChart("COP", "时间", chineseFont);
        addLine(cop, "COP", timestamps, values(result, SimulationStep::chillerCop), BLUE);

        Path absoluteTarget = target.toAbsolutePath();
        Path directory = absoluteTarget.getParent();
        Files.createDirectories(directory);
        Path temporary = directory.resolve(absoluteTarget.getFileName() + "." + UUID.randomUUID() + ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                BitmapEncoder.saveBitmap(List.of(temperature, power, cop), 3, 1, output, BitmapFormat.PNG);
            }
            replaceAtomically(temporary, absoluteTarget);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private XYChart baseChart(String yAxisTitle, String xAxisTitle, Font font) {
        XYChart chart = new XYChartBuilder()
                .width(WIDTH)
                .height(PANEL_HEIGHT)
                .xAxisTitle(xAxisTitle)
                .yAxisTitle(yAxisTitle)
                .build();
        chart.getStyler().setBaseFont(font);
        chart.getStyler().setChartTitleVisible(false);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
        chart.getStyler().setSeriesMarkers(new Marker[] {SeriesMarkers.NONE});
        chart.getStyler().setXAxisMaxLabelCount(8);
        chart.getStyler().setXAxisTickMarkSpacingHint(130);
        chart.getStyler().setDatePattern("yyyy-MM-dd");
        chart.getStyler().setTimezone(TimeZone.getDefault());
        return chart;
    }

    private void addLine(XYChart chart, String label, List<Date> x, List<Double> y, Color color) {
        XYSeries series = chart.addSeries(label, x, y);
        series.setLineColor(color);
        series.setSmooth(false);
    }

    private List<Double> values(SimulationResult result, ToDoubleFunction<SimulationStep> extractor) {
        return result.steps().stream().map(extractor::applyAsDouble).toList();
    }

    /** 必须确认字体实际覆盖中文，禁止静默回退成方框。 */
    private Font chooseChineseFont() throws IOException {
        Set<String> available = Arrays.stream(
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .collect(Collectors.toSet());
        for (String candidate : List.of("Microsoft YaHei", "SimHei", "Microsoft JhengHei")) {
            if (available.contains(candidate)) {
                Font font = new Font(candidate, Font.PLAIN, 14);
                if (font.canDisplayUpTo("温度功率时间") == -1) {
                    return font;
                }
            }
        }
        String windowsDirectory = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
        for (String fileName : List.of("msyh.ttc", "simhei.ttf", "msjh.ttc")) {
            Path fontFile = Path.of(windowsDirectory, "Fonts", fileName);
            if (!Files.isRegularFile(fontFile)) {
                continue;
            }
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile.toFile()).deriveFont(14.0f);
                if (font.canDisplayUpTo("温度功率时间") == -1) {
                    return font;
                }
            } catch (FontFormatException exception) {
                // 字体集合格式可能不被当前 JDK 接受，继续尝试下一种已确认字体。
            }
        }
        throw new IllegalStateException("未找到可显示中文的字体：Microsoft YaHei、SimHei、Microsoft JhengHei");
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
