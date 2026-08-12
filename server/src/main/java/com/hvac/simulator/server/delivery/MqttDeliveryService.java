package com.hvac.simulator.server.delivery;

import com.hvac.simulator.release.ModelVersion;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.CreateRequest;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.Created;
import com.hvac.simulator.server.api.dto.MqttDeliveryDtos.View;
import com.hvac.simulator.server.application.SimulationRunService;
import com.hvac.simulator.server.config.MqttDeliveryProperties;
import com.hvac.simulator.server.domain.MqttDelivery;
import com.hvac.simulator.server.domain.MqttDeliveryStatus;
import com.hvac.simulator.server.domain.MqttTimeMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class MqttDeliveryService {
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Shanghai");
    private final SimulationRunService runs;
    private final CentralHvacPointMapper mapper;
    private final MqttDeliveryProperties properties;
    private final MqttPublisher publisher;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, MqttDelivery> deliveries = new ConcurrentHashMap<>();

    @Autowired
    public MqttDeliveryService(
            SimulationRunService runs,
            CentralHvacPointMapper mapper,
            MqttDeliveryProperties properties,
            ObjectProvider<MqttPublisher> publisher,
            ExecutorService executor) {
        this(runs, mapper, properties, publisher.getIfAvailable(), executor);
    }

    MqttDeliveryService(
            SimulationRunService runs,
            CentralHvacPointMapper mapper,
            MqttDeliveryProperties properties,
            MqttPublisher publisher,
            ExecutorService executor) {
        this.runs = runs;
        this.mapper = mapper;
        this.properties = properties;
        this.publisher = publisher;
        this.executor = executor;
    }

    public Created create(UUID runId, CreateRequest request) {
        if (!properties.isEnabled() || publisher == null) {
            throw new IllegalStateException("MQTT 发送未启用");
        }
        var run = runs.completedRun(runId);
        if (run.parameters().version() != ModelVersion.GAIA_1_1) {
            throw new IllegalArgumentException("只有 Gaia 1.1 任务可以发送中央空调测点");
        }
        if (request == null || request.fromStep() == null || request.toStep() == null
                || request.timeMode() == null) {
            throw new IllegalArgumentException("发送范围和时间模式不能为空");
        }
        int size = run.output().gaia11().steps().size();
        int from = request.fromStep();
        int to = request.toStep();
        if (from < 0 || to < from || to >= size || to - from + 1 > 1_440) {
            throw new IllegalArgumentException("发送范围无效或超过 1,440 个时间步");
        }
        String buildingId = defaultValue(request.buildingId(), "BLD001");
        String deviceId = defaultValue(request.deviceId(), "WCR1");
        String coolingTowerDeviceId = defaultValue(request.coolingTowerDeviceId(), "TOWER1");
        if (request.targets() != null && request.targets().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("中央空调指标不能为空");
        }
        Set<CentralHvacMetricTarget> targets = request.targets() == null
                ? Set.of(CentralHvacMetricTarget.WCR_COP) : Set.copyOf(request.targets());
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个中央空调指标");
        }
        List<MqttPublishMessage> messages = prepareMessages(
                run.output().gaia11().steps(), from, to, request.timeMode(),
                buildingId, deviceId, coolingTowerDeviceId, targets);
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("所选范围没有冷却塔运行时间步");
        }
        var delivery = new MqttDelivery(UUID.randomUUID(), runId, messages.size());
        deliveries.put(delivery.id(), delivery);
        executor.execute(() -> publish(delivery, messages));
        return new Created(delivery.id(), MqttDeliveryStatus.QUEUED);
    }

    public View view(UUID runId, UUID deliveryId) {
        MqttDelivery delivery = deliveries.get(deliveryId);
        if (delivery == null || !delivery.runId().equals(runId)) {
            throw new NoSuchElementException("MQTT 发送任务不存在");
        }
        return new View(
                delivery.id(), delivery.runId(), delivery.status(), delivery.totalMessages(),
                delivery.successfulMessages(), delivery.failedMessages(), delivery.firstError(),
                delivery.createdAt());
    }

    private List<MqttPublishMessage> prepareMessages(
            List<com.hvac.simulator.simulation.Gaia11SimulationStep> steps,
            int from,
            int to,
            MqttTimeMode timeMode,
            String buildingId,
            String deviceId,
            String coolingTowerDeviceId,
            Set<CentralHvacMetricTarget> targets) {
        Instant rebaseStart = Instant.now().truncatedTo(ChronoUnit.MINUTES)
                .minus(to - from + 1L, ChronoUnit.MINUTES);
        List<MqttPublishMessage> messages = new ArrayList<>();
        for (int index = from; index <= to; index++) {
            var step = steps.get(index);
            long timestamp = timeMode == MqttTimeMode.ORIGINAL
                    ? step.timestamp().atZone(SOURCE_ZONE).toInstant().toEpochMilli()
                    : rebaseStart.plus(index - from, ChronoUnit.MINUTES).toEpochMilli();
            if (targets.contains(CentralHvacMetricTarget.WCR_COP)) {
                addMessages(messages, mapper.mapWcrCop(step, buildingId, deviceId, timestamp));
            }
            if (targets.contains(CentralHvacMetricTarget.TOWER_EFF)) {
                addMessages(messages, mapper.mapTowerEfficiency(
                        step, buildingId, coolingTowerDeviceId, timestamp));
            }
        }
        return List.copyOf(messages);
    }

    /** 发送固定消息快照，保证任务总数不受异步执行时模型或请求状态变化影响。 */
    private void publish(MqttDelivery delivery, List<MqttPublishMessage> messages) {
        delivery.start();
        for (MqttPublishMessage message : messages) {
            try {
                publisher.publish(message);
                delivery.success();
            } catch (Exception exception) {
                delivery.failure("MQTT 发布失败：" + exception.getClass().getSimpleName());
            }
        }
        delivery.finish();
    }

    private void addMessages(List<MqttPublishMessage> messages, List<CentralHvacPoint> points) {
        points.stream().map(point -> mapper.message(properties.getTopic(), point)).forEach(messages::add);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
