package com.hvac.simulator.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SimulationPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(SimulationPlatformApplication.class, args);
    }
}
