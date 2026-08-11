package com.hvac.simulator.server.domain;

public enum MqttDeliveryStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED
}
