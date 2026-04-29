package flowops.execution.support;

import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionType;
import java.time.LocalDateTime;
import java.util.List;

public final class ExecutionViewSupport {

    private ExecutionViewSupport() {
    }

    public static LocalDateTime executedAt(Execution execution, ExecutionStepLog log) {
        if (execution != null && execution.getStartedAt() != null) {
            return execution.getStartedAt();
        }
        if (log != null && log.getStartedAt() != null) {
            return log.getStartedAt();
        }
        return execution == null ? null : execution.getCreatedAt();
    }

    public static String caseName(Execution execution, ExecutionStepLog log) {
        if (log == null) {
            return null;
        }
        if (execution != null && execution.getExecutionType() == ExecutionType.SCENARIO) {
            return log.getScenarioStep() == null
                    ? log.getStepName()
                    : log.getScenarioStep().getScenario().getName();
        }
        return caseName(log);
    }

    public static String caseName(ExecutionStepLog log) {
        if (log == null) {
            return null;
        }
        if (log.getScenarioStep() != null) {
            return log.getScenarioStep().getScenario().getName();
        }
        String label = log.getTestCase() == null ? log.getStepName() : log.getTestCase().getName();
        return log.getMethod() == null ? label : log.getMethod() + " " + label;
    }

    public static String stepLabel(Execution execution, ExecutionStepLog log) {
        if (log == null || execution == null || execution.getExecutionType() != ExecutionType.SCENARIO || log.getScenarioStep() == null) {
            return null;
        }
        return stepLabel(log);
    }

    public static String stepLabel(ExecutionStepLog log) {
        if (log == null || log.getScenarioStep() == null) {
            return null;
        }
        return "Step " + log.getScenarioStep().getStepOrder() + ": " + log.getScenarioStep().getLabel();
    }

    public static Long durationMs(Execution execution, ExecutionStepLog log) {
        if (log != null && log.getDurationMs() != null) {
            return log.getDurationMs();
        }
        return execution == null ? null : execution.getAvgDurationMs();
    }

    public static ExecutionStepLog firstLog(List<ExecutionStepLog> logs) {
        return logs == null || logs.isEmpty() ? null : logs.get(0);
    }

    public static ExecutionStepLog focusLog(List<ExecutionStepLog> logs) {
        ExecutionStepLog firstLog = firstLog(logs);
        if (logs == null || logs.isEmpty()) {
            return null;
        }
        return logs.stream()
                .filter(log -> log.getErrorMessage() != null && !log.getErrorMessage().isBlank())
                .findFirst()
                .orElse(firstLog);
    }
}
