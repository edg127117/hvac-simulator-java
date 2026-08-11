package com.hvac.simulator.server.delivery;

import java.util.Objects;

/** 发送端口的完整消息契约，QoS 和 retain 由映射层锁定。 */
public record MqttPublishMessage(String topic, int qos, boolean retained, String payload) {
    public MqttPublishMessage {
        Objects.requireNonNull(topic, "MQTT topic 不能为空");
        Objects.requireNonNull(payload, "MQTT payload 不能为空");
        if (topic.isBlank() || qos != 1 || retained) {
            throw new IllegalArgumentException("中央空调 MQTT 必须使用非空 topic、QoS 1、retain=false");
        }
    }
}
