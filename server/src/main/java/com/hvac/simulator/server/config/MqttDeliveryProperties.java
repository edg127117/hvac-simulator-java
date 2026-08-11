package com.hvac.simulator.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** MQTT 凭据和 Broker 地址只从外部配置注入，不进入任务快照或日志。 */
@ConfigurationProperties("mqtt.delivery")
public class MqttDeliveryProperties {
    private boolean enabled;
    private String brokerUrl = "tcp://127.0.0.1:1883";
    private String clientId = "hvac-simulator-gaia11";
    private String username = "";
    private String password = "";
    private String topic = "device/data/up";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
}
