package com.hvac.simulator.output;

import com.hvac.simulator.simulation.Gaia11SimulationResult;
import com.hvac.simulator.simulation.Gaia11SimulationStep;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
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

/** 从同一份 Gaia 1.1 Java 结果生成温度、负荷、功率、COP 和水温五联图。 */
public final class Gaia11ChartRenderer {
    private static final int WIDTH = 1_400;
    private static final int PANEL_HEIGHT = 280;

    public void write(Gaia11SimulationResult result, Path target) throws IOException {
        Font font = chooseFont();
        List<Date> timestamps = result.steps().stream()
                .map(step -> Date.from(step.timestamp().atZone(ZoneId.systemDefault()).toInstant()))
                .toList();
        XYChart temperature = chart("温度 (℃)", "", font);
        line(temperature, "室温", timestamps, values(result, Gaia11SimulationStep::roomC), new Color(31, 119, 180));
        line(temperature, "室外温度", timestamps, values(result, Gaia11SimulationStep::outdoorC), new Color(255, 127, 14));
        XYChart load = chart("功率 (kW)", "", font);
        line(load, "冷负荷", timestamps, values(result, Gaia11SimulationStep::coolingLoadKw), new Color(31, 119, 180));
        line(load, "系统总功率（测量）", timestamps, values(result, Gaia11SimulationStep::totalPowerKw), new Color(255, 127, 14));
        XYChart power = chart("冷机功率 (kW)", "", font);
        line(power, "冷机功率（理论）", timestamps, values(result, Gaia11SimulationStep::chillerPowerTrueKw), new Color(31, 119, 180));
        line(power, "冷机功率（测量）", timestamps, values(result, Gaia11SimulationStep::chillerPowerKw), Color.RED);
        XYChart cop = chart("COP", "", font);
        line(cop, "COP（理论）", timestamps, values(result, Gaia11SimulationStep::chillerCop), new Color(31, 119, 180));
        line(cop, "COP（测量）", timestamps, values(result, Gaia11SimulationStep::measuredCop), new Color(44, 160, 44));
        XYChart water = chart("水温 (℃)", "时间", font);
        line(water, "冷冻水供水（测量）", timestamps, values(result, Gaia11SimulationStep::chilledWaterSupplySensorC), Color.BLUE);
        line(water, "冷冻水回水（测量）", timestamps, values(result, Gaia11SimulationStep::chilledWaterReturnSensorC), Color.CYAN.darker());
        line(water, "冷却水供水（测量）", timestamps, values(result, Gaia11SimulationStep::coolingWaterSupplySensorC), Color.RED);
        line(water, "冷却水回水（测量）", timestamps, values(result, Gaia11SimulationStep::coolingWaterReturnSensorC), Color.ORANGE);

        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        try (OutputStream output = Files.newOutputStream(absolute)) {
            BitmapEncoder.saveBitmap(List.of(temperature, load, power, cop, water), 5, 1, output, BitmapFormat.PNG);
        }
    }

    private XYChart chart(String yTitle, String xTitle, Font font) {
        XYChart chart = new XYChartBuilder().width(WIDTH).height(PANEL_HEIGHT)
                .yAxisTitle(yTitle).xAxisTitle(xTitle).build();
        chart.getStyler().setBaseFont(font);
        chart.getStyler().setChartTitleVisible(false);
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        chart.getStyler().setPlotBackgroundColor(Color.WHITE);
        chart.getStyler().setLegendPosition(LegendPosition.InsideNE);
        chart.getStyler().setSeriesMarkers(new Marker[] {SeriesMarkers.NONE});
        chart.getStyler().setXAxisMaxLabelCount(8);
        chart.getStyler().setDatePattern("yyyy-MM-dd");
        chart.getStyler().setTimezone(TimeZone.getDefault());
        return chart;
    }

    private void line(XYChart chart, String name, List<Date> x, List<Double> y, Color color) {
        XYSeries series = chart.addSeries(name, x, y);
        series.setLineColor(color);
        series.setSmooth(false);
    }

    private List<Double> values(Gaia11SimulationResult result, ToDoubleFunction<Gaia11SimulationStep> extractor) {
        return result.steps().stream().map(extractor::applyAsDouble).toList();
    }

    private Font chooseFont() {
        Set<String> available = Arrays.stream(
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                .collect(Collectors.toSet());
        for (String candidate : List.of("Microsoft YaHei", "SimHei", "Microsoft JhengHei", "Dialog")) {
            if (available.contains(candidate)) {
                Font font = new Font(candidate, Font.PLAIN, 14);
                if (font.canDisplayUpTo("温度功率时间冷冻水测量") == -1) {
                    return font;
                }
            }
        }
        throw new IllegalStateException("未找到可显示中文的字体");
    }
}
