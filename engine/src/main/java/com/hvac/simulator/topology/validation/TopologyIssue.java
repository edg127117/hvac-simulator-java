package com.hvac.simulator.topology.validation;

import java.util.Objects;

public record TopologyIssue(
        TopologyIssueCode code,
        TopologyIssueSeverity severity,
        String message,
        String elementId) {

    public TopologyIssue {
        Objects.requireNonNull(code, "拓扑问题编码不能为空");
        Objects.requireNonNull(severity, "拓扑问题级别不能为空");
        if (message == null || message.isBlank() || elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("拓扑问题说明和关联元素不能为空");
        }
    }
}
