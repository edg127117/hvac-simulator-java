package com.hvac.simulator.topology.validation;

import java.util.List;
import java.util.Objects;

public record TopologyValidationResult(List<TopologyIssue> issues) {
    public TopologyValidationResult {
        Objects.requireNonNull(issues, "拓扑问题集合不能为空");
        if (issues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("拓扑问题集合不能包含空值");
        }
        issues = List.copyOf(issues);
    }

    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.severity() == TopologyIssueSeverity.ERROR);
    }
}
