package com.hvac.simulator.server.api.dto;

import com.hvac.simulator.server.domain.MqttDeliveryStatus;
import com.hvac.simulator.server.domain.MqttTimeMode;
import com.hvac.simulator.server.delivery.CentralHvacMetricTarget;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class MqttDeliveryDtos {
    private MqttDeliveryDtos() {}

    public record CreateRequest(
            Integer fromStep, Integer toStep, MqttTimeMode timeMode,
            String buildingId, String deviceId, String coolingTowerDeviceId,
            Set<CentralHvacMetricTarget> targets) {

        public CreateRequest(
                Integer fromStep, Integer toStep, MqttTimeMode timeMode,
                String buildingId, String deviceId) {
            this(fromStep, toStep, timeMode, buildingId, deviceId, null, null);
        }
    }

    public record Created(UUID deliveryId, MqttDeliveryStatus status) {}

    public record View(
            UUID deliveryId, UUID runId, MqttDeliveryStatus status,
            int totalMessages, int successfulMessages, int failedMessages,
            String firstError, Instant createdAt) {}
}
