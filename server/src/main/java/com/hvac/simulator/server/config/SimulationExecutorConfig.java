package com.hvac.simulator.server.config;

import com.hvac.simulator.release.ModelReleaseCatalog;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationExecutorConfig {
    @Bean
    ModelReleaseCatalog modelReleaseCatalog() {
        return new ModelReleaseCatalog();
    }

    /** MVP 串行执行，避免多个 10,080 步结果同时占用内存。 */
    @Bean(destroyMethod = "shutdown")
    ExecutorService simulationExecutor() {
        return Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("simulation-runner").factory());
    }
}
