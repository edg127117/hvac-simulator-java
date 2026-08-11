package com.hvac.simulator.server.domain;

import java.time.Instant;
import java.util.UUID;

/** MQTT 回放状态独立于仿真任务，发送失败不会改写模型结果。 */
public final class MqttDelivery {
    private final UUID id;
    private final UUID runId;
    private final int totalMessages;
    private final Instant createdAt = Instant.now();
    private volatile MqttDeliveryStatus status = MqttDeliveryStatus.QUEUED;
    private volatile int successfulMessages;
    private volatile int failedMessages;
    private volatile String firstError;

    public MqttDelivery(UUID id, UUID runId, int totalMessages) {
        this.id = id;
        this.runId = runId;
        this.totalMessages = totalMessages;
    }

    public void start() { status = MqttDeliveryStatus.RUNNING; }
    public void success() { successfulMessages++; }
    public void failure(String message) {
        failedMessages++;
        if (firstError == null) {
            firstError = message;
        }
    }
    public void finish() {
        status = failedMessages == 0 ? MqttDeliveryStatus.COMPLETED
                : successfulMessages == 0 ? MqttDeliveryStatus.FAILED
                : MqttDeliveryStatus.PARTIAL_FAILED;
    }

    public UUID id() { return id; }
    public UUID runId() { return runId; }
    public int totalMessages() { return totalMessages; }
    public Instant createdAt() { return createdAt; }
    public MqttDeliveryStatus status() { return status; }
    public int successfulMessages() { return successfulMessages; }
    public int failedMessages() { return failedMessages; }
    public String firstError() { return firstError; }
}
