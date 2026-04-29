package flowops.execution.dto.response;

import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionTriggerSource;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.support.ExecutionViewSupport;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record ExecutionDetailResponse(
        @Schema(description = "실행 ID", example = "700")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "환경명", example = "Production")
        String environmentName,
        @Schema(description = "실행 유형", example = "SCENARIO")
        ExecutionType executionType,
        @Schema(description = "실행 대상 ID", example = "300")
        Long targetId,
        @Schema(description = "트리거 소스", example = "MANUAL")
        ExecutionTriggerSource triggerSource,
        @Schema(description = "테스트 레벨", example = "REGRESSION")
        TestLevel testLevel,
        @Schema(description = "실행 상태", example = "PARTIAL_FAILED")
        ExecutionStatus status,
        @Schema(description = "실행 시간", example = "2026-04-12T03:10:00")
        LocalDateTime executedAt,
        @Schema(description = "실행 케이스명 또는 시나리오명", example = "POST 결제 승인 정상 흐름")
        String caseName,
        @Schema(description = "시나리오 스텝 표시", example = "Step 1: Login")
        String step,
        @Schema(description = "총 실행 건수", example = "4")
        Integer totalCount,
        @Schema(description = "성공 건수", example = "3")
        Integer passedCount,
        @Schema(description = "실패 건수", example = "1")
        Integer failedCount,
        @Schema(description = "평균 실행 시간(ms)", example = "175")
        Long avgDurationMs,
        @Schema(description = "대표 Duration(ms)", example = "150")
        Long durationMs,
        @Schema(description = "엔드포인트", example = "/payments")
        String endpoint,
        @Schema(description = "환경 기본 헤더 JSON", example = "{\"Authorization\":\"Bearer ***\"}")
        String headers,
        @Schema(description = "요청 Body", example = "{\"request\":\"mock\"}")
        String body,
        @Schema(description = "응답 Body", example = "{\"result\":\"mock success\"}")
        String response,
        @Schema(description = "응답 상태 코드", example = "200")
        Integer statusCode,
        @Schema(description = "응답 시간(ms)", example = "150")
        Long responseTimeMs,
        @Schema(description = "검증 설정", example = "{\"assertions\":[\"status == 200\"]}")
        String validationConfig,
        @Schema(description = "오류 메시지", example = "Mock execution failure for placeholder engine.")
        String errorMessage,
        @Schema(description = "기대 동작", example = "응답 상태는 200이어야 합니다.")
        String expectedBehavior,
        @Schema(description = "실제 동작", example = "실제 응답은 500 에러와 오류 본문을 반환했습니다.")
        String actualBehavior,
        @Schema(description = "실행 요청자", example = "qa.engineer@flowops.dev")
        String createdBy,
        @Schema(description = "실행 시작 시각", example = "2026-04-12T03:10:00")
        LocalDateTime startedAt,
        @Schema(description = "실행 종료 시각", example = "2026-04-12T03:12:00")
        LocalDateTime endedAt,
        @Schema(description = "실행 생성 시각", example = "2026-04-12T03:09:58")
        LocalDateTime createdAt,
        @Schema(description = "타임라인 로그")
        List<ExecutionStepLogResponse> timeline
) {

    public static ExecutionDetailResponse of(Execution execution, List<ExecutionStepLog> logs, List<ExecutionStepLogResponse> timeline) {
        ExecutionStepLog firstLog = ExecutionViewSupport.firstLog(logs);
        ExecutionStepLog focusLog = ExecutionViewSupport.focusLog(logs);
        return new ExecutionDetailResponse(
                execution.getId(),
                execution.getApp().getId(),
                execution.getEnvironment() == null ? null : execution.getEnvironment().getId(),
                execution.getEnvironment() == null ? null : execution.getEnvironment().getName(),
                execution.getExecutionType(),
                execution.getTargetId(),
                execution.getTriggerSource(),
                execution.getTestLevel(),
                execution.getStatus(),
                ExecutionViewSupport.executedAt(execution, firstLog),
                ExecutionViewSupport.caseName(execution, firstLog),
                ExecutionViewSupport.stepLabel(execution, firstLog),
                execution.getTotalCount(),
                execution.getPassedCount(),
                execution.getFailedCount(),
                execution.getAvgDurationMs(),
                ExecutionViewSupport.durationMs(execution, firstLog),
                firstLog == null ? null : firstLog.getPath(),
                execution.getEnvironment() == null ? null : execution.getEnvironment().getHeaders(),
                focusLog == null ? null : focusLog.getRequestBody(),
                focusLog == null ? null : focusLog.getResponseBody(),
                focusLog == null ? null : focusLog.getResponseCode(),
                ExecutionViewSupport.durationMs(execution, focusLog),
                validationConfig(focusLog),
                focusLog == null ? null : focusLog.getErrorMessage(),
                expectedBehavior(focusLog),
                actualBehavior(focusLog),
                execution.getCreatedBy(),
                execution.getStartedAt(),
                execution.getEndedAt(),
                execution.getCreatedAt(),
                timeline
        );
    }

    private static String validationConfig(ExecutionStepLog firstLog) {
        if (firstLog == null) {
            return null;
        }
        if (firstLog.getTestCase() != null) {
            return firstLog.getTestCase().getAssertionSpec();
        }
        if (firstLog.getScenarioStep() != null) {
            return firstLog.getScenarioStep().getValidationRules();
        }
        return null;
    }

    private static String expectedBehavior(ExecutionStepLog log) {
        if (log == null) {
            return null;
        }
        if (log.getTestCase() != null && log.getTestCase().getExpectedSpec() != null) {
            return log.getTestCase().getExpectedSpec();
        }
        if (log.getScenarioStep() != null && log.getScenarioStep().getValidationRules() != null) {
            return log.getScenarioStep().getValidationRules();
        }
        if (log.getResponseCode() != null) {
            return "응답 상태는 200이어야 합니다. 현재 설정된 검증 규칙이 없어서 기본 기대값을 사용합니다.";
        }
        return null;
    }

    private static String actualBehavior(ExecutionStepLog log) {
        if (log == null) {
            return null;
        }
        if (log.getResponseCode() == null && log.getResponseBody() == null) {
            return null;
        }
        return "실제 응답은 "
                + (log.getResponseCode() == null ? "상태 코드 없음" : log.getResponseCode())
                + " 이고 응답 본문은 "
                + (log.getResponseBody() == null ? "없습니다." : log.getResponseBody());
    }
}
