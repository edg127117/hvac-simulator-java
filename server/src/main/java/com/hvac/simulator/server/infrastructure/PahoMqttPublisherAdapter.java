package com.hvac.simulator.server.infrastructure;

import com.hvac.simulator.server.config.MqttDeliveryProperties;
import com.hvac.simulator.server.delivery.MqttPublishMessage;
import com.hvac.simulator.server.delivery.MqttPublisher;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Paho 只负责连接、QoS 1 PUBACK 和字节发送，不理解仿真字段。 */
@Component
@ConditionalOnProperty(prefix = "mqtt.delivery", name = "enabled", havingValue = "true")
public class PahoMqttPublisherAdapter implements MqttPublisher, AutoCloseable {
    private final MqttDeliveryProperties properties;
    private MqttClient client;

    public PahoMqttPublisherAdapter(MqttDeliveryProperties properties) {
        this.properties = properties;
    }

    @Override
    public synchronized void publish(MqttPublishMessage message) throws Exception {
        ensureConnected();
        MqttMessage mqttMessage = new MqttMessage(message.payload().getBytes(StandardCharsets.UTF_8));
        mqttMessage.setQos(message.qos());
        mqttMessage.setRetained(message.retained());
        client.getTopic(message.topic()).publish(mqttMessage).waitForCompletion(10_000L);
    }

    private void ensureConnected() throws Exception {
        if (client != null && client.isConnected()) {
            return;
        }
        client = new MqttClient(
                properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        if (!properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername());
        }
        if (!properties.getPassword().isBlank()) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        client.connect(options);
    }

    @Override
    public synchronized void close() throws Exception {
        if (client != null) {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        }
    }
}
