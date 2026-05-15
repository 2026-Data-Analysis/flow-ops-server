package flowops.execution.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.TestValidationResult;
import java.time.LocalDateTime;
import java.util.List;

public record ExecutionLogDetailResponse(
        Long stepId,
        LocalDateTime timestamp,
        String executionName,
        String stepName,
        String testCaseName,
        String scenarioName,
        String category,
        String environment,
        String method,
        String apiEndpoint,
        String path,
        ExecutionStepStatus status,
        Long durationMs,
        Integer statusCode,
        Summary summary,
        Request request,
        Response response,
        Validation validation
) {

    public static ExecutionLogDetailResponse of(
            ExecutionStepLog log,
            JsonNode headers,
            JsonNode requestBody,
            JsonNode responseBody,
            List<TestValidationResult> validationResults,
            List<ExecutionStepLog> executionTimeline
    ) {
        List<Assertion> assertions = validationResults.stream()
                .map(Assertion::from)
                .toList();
        String expected = validationResults.stream()
                .map(TestValidationResult::getExpectedValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        String actual = validationResults.stream()
                .map(TestValidationResult::getActualValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);

        return new ExecutionLogDetailResponse(
                log.getId(),
                log.getStartedAt() == null ? log.getCreatedAt() : log.getStartedAt(),
                log.getExecution().getName(),
                log.getStepName(),
                testCaseName(log),
                scenarioName(log),
                category(log),
                log.getExecution().getEnvironment() == null ? null : log.getExecution().getEnvironment().getName(),
                log.getMethod(),
                log.getPath(),
                log.getPath(),
                log.getStatus(),
                log.getDurationMs(),
                log.getResponseCode(),
                Summary.from(log),
                new Request(log.getMethod(), log.getPath(), headers, requestBody),
                new Response(log.getResponseCode(), responseBody, log.getDurationMs()),
                new Validation(
                        log.getErrorMessage(),
                        expected,
                        actual,
                        assertions,
                        executionTimeline.stream().map(TimelineStep::from).toList()
                )
        );
    }

    public record Summary(
            Long executionId,
            Long stepId,
            LocalDateTime timestamp,
            String executionName,
            String stepName,
            String testCaseName,
            String scenarioName,
            String category,
            String environment,
            ExecutionStepStatus status,
            Long durationMs
    ) {

        public static Summary from(ExecutionStepLog log) {
            return new Summary(
                    log.getExecution().getId(),
                    log.getId(),
                    log.getStartedAt() == null ? log.getCreatedAt() : log.getStartedAt(),
                    log.getExecution().getName(),
                    log.getStepName(),
                    ExecutionLogDetailResponse.testCaseName(log),
                    ExecutionLogDetailResponse.scenarioName(log),
                    ExecutionLogDetailResponse.category(log),
                    log.getExecution().getEnvironment() == null ? null : log.getExecution().getEnvironment().getName(),
                    log.getStatus(),
                    log.getDurationMs()
            );
        }
    }

    public record Request(String method, String apiEndpoint, JsonNode header, JsonNode body) {
    }

    public record Response(Integer statusCode, JsonNode body, Long responseTimeMs) {
    }

    public record Validation(
            String errorMessage,
            String expected,
            String actual,
            List<Assertion> assertions,
            List<TimelineStep> executionTimeLine
    ) {
    }

    public record Assertion(String name, String expected, String actual, boolean passed, String message) {

        public static Assertion from(TestValidationResult result) {
            return new Assertion(
                    result.getAssertionName(),
                    result.getExpectedValue(),
                    result.getActualValue(),
                    result.isPassed(),
                    result.getMessage()
            );
        }
    }

    public record TimelineStep(
            Long stepId,
            Integer stepOrder,
            String stepName,
            String method,
            String apiEndpoint,
            ExecutionStepStatus status,
            Long responseTimeMs
    ) {

        public static TimelineStep from(ExecutionStepLog log) {
            return new TimelineStep(
                    log.getId(),
                    log.getStepOrder(),
                    log.getStepName(),
                    log.getMethod(),
                    log.getPath(),
                    log.getStatus(),
                    log.getDurationMs()
            );
        }
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
        if (log.getScenarioStep() != null) {
            return "Scenario";
        }
        return "TestCase";
    }
}
