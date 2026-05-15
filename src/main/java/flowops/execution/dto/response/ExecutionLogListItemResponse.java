package flowops.execution.dto.response;

import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.ExecutionType;
import java.time.LocalDateTime;

public record ExecutionLogListItemResponse(
        Long executionId,
        Long stepId,
        LocalDateTime timestamp,
        String testCaseName,
        String scenarioName,
        String category,
        String executionName,
        String stepName,
        String method,
        String apiEndpoint,
        String path,
        ExecutionStepStatus status,
        Long durationMs,
        String environment,
        String testLevel
) {

    public static ExecutionLogListItemResponse from(ExecutionStepLog log) {
        return new ExecutionLogListItemResponse(
                log.getExecution().getId(),
                log.getId(),
                log.getStartedAt() == null ? log.getCreatedAt() : log.getStartedAt(),
                testCaseName(log),
                scenarioName(log),
                category(log),
                log.getExecution().getName(),
                log.getStepName(),
                log.getMethod(),
                log.getPath(),
                log.getPath(),
                log.getStatus(),
                log.getDurationMs(),
                log.getExecution().getEnvironment() == null ? null : log.getExecution().getEnvironment().getName(),
                log.getExecution().getTestLevel().name()
        );
    }

    private static String testCaseName(ExecutionStepLog log) {
        if (log.getScenarioStep() != null) {
            return null;
        }
        if (log.getTestCase() != null) {
            return log.getTestCase().getName();
        }
        return log.getExecution().getName();
    }

    private static String scenarioName(ExecutionStepLog log) {
        if (log.getScenarioStep() == null) {
            return null;
        }
        return log.getScenarioStep().getScenario().getName();
    }

    private static String category(ExecutionStepLog log) {
        if (log.getExecution().getExecutionType() == ExecutionType.SCENARIO || log.getScenarioStep() != null) {
            return "Scenario";
        }
        return "TestCase";
    }
}
