package flowops.execution.dto.response;

import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.support.ExecutionViewSupport;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record ExecutionSummaryResponse(
        @Schema(description = "실행 ID", example = "700")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "실행 유형", example = "API_BATCH")
        ExecutionType executionType,
        @Schema(description = "실행 시간", example = "2026-04-12T03:10:00")
        LocalDateTime executedAt,
        @Schema(description = "실행 케이스명 또는 시나리오명", example = "POST 결제 승인 정상 흐름")
        String caseName,
        @Schema(description = "시나리오 스텝 표시", example = "Step 1: Login")
        String step,
        @Schema(description = "HTTP 메서드", example = "POST")
        String method,
        @Schema(description = "엔드포인트", example = "/payments")
        String endpoint,
        @Schema(description = "성공 여부", example = "true")
        boolean success,
        @Schema(description = "실행 시간(ms)", example = "150")
        Long durationMs,
        @Schema(description = "총 테스트 수", example = "4")
        Integer totalTestCount,
        @Schema(description = "성공 수", example = "3")
        Integer passedCount,
        @Schema(description = "실패 수", example = "1")
        Integer failedCount,
        @Schema(description = "평균 실행 시간(ms)", example = "175")
        Long avgDurationMs,
        @Schema(description = "테스트 레벨", example = "REGRESSION")
        TestLevel testLevel
) {

    public static ExecutionSummaryResponse from(Execution execution, List<ExecutionStepLog> logs) {
        ExecutionStepLog firstLog = ExecutionViewSupport.firstLog(logs);
        return new ExecutionSummaryResponse(
                execution.getId(),
                execution.getApp().getId(),
                execution.getEnvironment() == null ? null : execution.getEnvironment().getId(),
                execution.getExecutionType(),
                ExecutionViewSupport.executedAt(execution, firstLog),
                ExecutionViewSupport.caseName(execution, firstLog),
                ExecutionViewSupport.stepLabel(execution, firstLog),
                firstLog == null ? null : firstLog.getMethod(),
                firstLog == null ? null : firstLog.getPath(),
                safe(execution.getFailedCount()) == 0,
                ExecutionViewSupport.durationMs(execution, firstLog),
                execution.getTotalCount(),
                execution.getPassedCount(),
                execution.getFailedCount(),
                execution.getAvgDurationMs(),
                execution.getTestLevel()
        );
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
