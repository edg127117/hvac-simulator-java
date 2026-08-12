package com.hvac.simulator.server.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hvac.simulator.release.ModelReleaseCatalog;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.CreateRequest;
import com.hvac.simulator.server.api.dto.SimulationRunDtos;
import com.hvac.simulator.server.application.SimulationRunService;
import com.hvac.simulator.server.config.MqttDeliveryProperties;
import com.hvac.simulator.server.domain.MqttDeliveryStatus;
import com.hvac.simulator.server.domain.MqttTimeMode;
import com.hvac.simulator.server.domain.SimulationMode;
import com.hvac.simulator.server.infrastructure.InMemorySimulationRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MqttDeliveryServiceTest {
    private ExecutorService executor;
    private SimulationRunService runs;
    private MqttDeliveryService deliveries;
    private ArrayList<MqttPublishMessage> messages;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        runs = new SimulationRunService(
                new ModelReleaseCatalog(), new InMemorySimulationRunRepository(), executor);
        var properties = new MqttDeliveryProperties();
        properties.setEnabled(true);
        messages = new ArrayList<>();
        deliveries = new MqttDeliveryService(
                runs, new CentralHvacPointMapper(), properties, messages::add, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void publishesFourMessagesPerStepWithoutChangingSimulation() throws Exception {
        var run = runs.create(new SimulationRunDtos.CreateRequest(
                "gaia-1.1", SimulationMode.BASELINE, 20240810L, Map.of()));
        awaitRun(run.runId());

        var created = deliveries.create(run.runId(), new CreateRequest(
                0, 2, MqttTimeMode.REBASE_TO_NOW, "BLD001", "WCR1"));
        var view = awaitDelivery(run.runId(), created.deliveryId());

        assertEquals(MqttDeliveryStatus.COMPLETED, view.status());
        assertEquals(12, view.totalMessages());
        assertEquals(12, view.successfulMessages());
        assertEquals(12, messages.size());
        assertEquals("device/data/up", messages.getFirst().topic());
        assertEquals("COMPLETED", runs.view(run.runId()).status().name());
    }

    @Test
    void rejectsGaia10AndDisabledDelivery() throws Exception {
        var run = runs.create(new SimulationRunDtos.CreateRequest(
                "gaia-1.0", SimulationMode.BASELINE, 42L, Map.of()));
        awaitRun(run.runId());
        assertThrows(IllegalArgumentException.class, () -> deliveries.create(
                run.runId(), new CreateRequest(0, 0, MqttTimeMode.ORIGINAL, null, null)));
    }

    @Test
    void publishesSelectedWcrAndTowerTargetsUsingActualPointCount() throws Exception {
        var run = runs.create(new SimulationRunDtos.CreateRequest(
                "gaia-1.1", SimulationMode.BASELINE, 20240810L, Map.of()));
        awaitRun(run.runId());
        var steps = runs.completedRun(run.runId()).output().gaia11().steps();
        int activeIndex = java.util.stream.IntStream.range(0, steps.size())
                .filter(index -> steps.get(index).coolingTowerFanPowerKw() > 0.0
                        && steps.get(index).coolingWaterFlowSensorM3PerSecond() > 0.0)
                .findFirst().orElseThrow();

        var created = deliveries.create(run.runId(), new CreateRequest(
                activeIndex, activeIndex, MqttTimeMode.REBASE_TO_NOW,
                "BLD001", "WCR1", "TOWER1",
                Set.of(CentralHvacMetricTarget.WCR_COP, CentralHvacMetricTarget.TOWER_EFF)));
        var view = awaitDelivery(run.runId(), created.deliveryId());

        assertEquals(MqttDeliveryStatus.COMPLETED, view.status());
        assertEquals(7, view.totalMessages());
        assertEquals(7, messages.size());
        assertEquals(List.of(
                "WCR1_TWin", "WCR1_TWout", "WCR1_Flow", "WCR1_PPE",
                "TOWER1_TCWin", "TOWER1_TCWout", "TOWER1_TWB"),
                messages.stream().map(MqttDeliveryServiceTest::pointCode).toList());
    }

    @Test
    void rejectsTowerOnlyDeliveryWhenSelectedRangeHasNoRunningTower() throws Exception {
        var run = runs.create(new SimulationRunDtos.CreateRequest(
                "gaia-1.1", SimulationMode.BASELINE, 20240810L, Map.of()));
        awaitRun(run.runId());
        var steps = runs.completedRun(run.runId()).output().gaia11().steps();
        int stoppedIndex = java.util.stream.IntStream.range(0, steps.size())
                .filter(index -> steps.get(index).coolingTowerFanPowerKw() == 0.0)
                .findFirst().orElseThrow();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> deliveries.create(run.runId(), new CreateRequest(
                        stoppedIndex, stoppedIndex, MqttTimeMode.REBASE_TO_NOW,
                        "BLD001", "WCR1", "TOWER1",
                        Set.of(CentralHvacMetricTarget.TOWER_EFF))));

        assertEquals("所选范围没有冷却塔运行时间步", error.getMessage());

        IllegalArgumentException emptyTargets = assertThrows(IllegalArgumentException.class,
                () -> deliveries.create(run.runId(), new CreateRequest(
                        stoppedIndex, stoppedIndex, MqttTimeMode.REBASE_TO_NOW,
                        "BLD001", "WCR1", "TOWER1", Set.of())));
        assertEquals("至少选择一个中央空调指标", emptyTargets.getMessage());
    }

    private static String pointCode(MqttPublishMessage message) {
        String marker = "\"pointCode\":\"";
        int start = message.payload().indexOf(marker) + marker.length();
        return message.payload().substring(start, message.payload().indexOf('"', start));
    }

    private void awaitRun(java.util.UUID runId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (runs.view(runId).status().name().matches("QUEUED|RUNNING")
                && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
        }
        assertEquals("COMPLETED", runs.view(runId).status().name());
    }

    private com.hvac.simulator.server.api.dto.MqttDeliveryDtos.View awaitDelivery(
            java.util.UUID runId, java.util.UUID deliveryId) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        var view = deliveries.view(runId, deliveryId);
        while (view.status().name().matches("QUEUED|RUNNING") && Instant.now().isBefore(deadline)) {
            Thread.onSpinWait();
            view = deliveries.view(runId, deliveryId);
        }
        return view;
    }
}
