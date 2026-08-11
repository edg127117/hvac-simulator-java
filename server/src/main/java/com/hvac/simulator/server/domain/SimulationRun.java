package com.hvac.simulator.server.domain;

import com.hvac.simulator.release.ModelParameterSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** 保存一次运行的参数快照、状态、进度和 Java 结果；状态变化由服务串行驱动。 */
public final class SimulationRun {
    private final UUID id;
    private final SimulationMode mode;
    private final long seed;
    private final ModelParameterSnapshot parameters;
    private final Instant createdAt;
    private volatile SimulationRunStatus status = SimulationRunStatus.QUEUED;
    private volatile int completedSteps;
    private volatile int totalSteps;
    private volatile LocalDateTime simulationTime;
    private volatile String errorCode;
    private volatile String errorMessage;
    private volatile SimulationRunOutput output;

    public SimulationRun(UUID id, SimulationMode mode, long seed, ModelParameterSnapshot parameters) {
        this.id = Objects.requireNonNull(id);
        this.mode = Objects.requireNonNull(mode);
        this.seed = seed;
        this.parameters = Objects.requireNonNull(parameters);
        createdAt = Instant.now();
    }

    public synchronized void start(int steps) {
        requireStatus(SimulationRunStatus.QUEUED);
        status = SimulationRunStatus.RUNNING;
        totalSteps = steps;
    }

    public void progress(int completed, int total, LocalDateTime time) {
        if (status != SimulationRunStatus.RUNNING) {
            return;
        }
        completedSteps = completed;
        totalSteps = total;
        simulationTime = time;
    }

    public synchronized void complete(SimulationRunOutput completedOutput) {
        requireStatus(SimulationRunStatus.RUNNING);
        output = Objects.requireNonNull(completedOutput);
        completedSteps = totalSteps;
        status = SimulationRunStatus.COMPLETED;
    }

    public synchronized void fail(String code, String message) {
        if (status != SimulationRunStatus.QUEUED && status != SimulationRunStatus.RUNNING) {
            throw new IllegalStateException("已结束任务不能再次失败");
        }
        errorCode = code;
        errorMessage = message;
        status = SimulationRunStatus.FAILED;
    }

    private void requireStatus(SimulationRunStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("任务状态错误：预期=" + expected + "，实际=" + status);
        }
    }

    public UUID id() { return id; }
    public SimulationMode mode() { return mode; }
    public long seed() { return seed; }
    public ModelParameterSnapshot parameters() { return parameters; }
    public Instant createdAt() { return createdAt; }
    public SimulationRunStatus status() { return status; }
    public int completedSteps() { return completedSteps; }
    public int totalSteps() { return totalSteps; }
    public LocalDateTime simulationTime() { return simulationTime; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }
    public SimulationRunOutput output() { return output; }
}
