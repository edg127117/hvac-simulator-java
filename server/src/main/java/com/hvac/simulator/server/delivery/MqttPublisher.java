package com.hvac.simulator.server.delivery;

/** MQTT 发送应用端口；实现类不得包含 HVAC 公式。 */
public interface MqttPublisher {
    void publish(MqttPublishMessage message) throws Exception;
}
