package flowops.execution.dto.response;

import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.support.ExecutionViewSupport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ExecutionStepLogResponse(
        @Schema(description = "로그 ID", example = "8001")
        Long id,
        @Schema(description = "실행 ID", example = "700")
        Long executionId,
        @Schema(description = "테스트 케이스 ID", example = "501")
        Long testCaseId,
        @Schema(description = "시나리오 스텝 ID", example = "901")
        Long scenarioStepId,
        @Schema(description = "실행 시간", example = "2026-04-12T03:10:02")
        LocalDateTime executedAt,
        @Schema(description = "실행 케이스명", example = "POST 결제 승인 정상 흐름")
        String caseName,
        @Schema(description = "시나리오 스텝 표시", example = "Step 1: Login")
        String step,
        @Schema(description = "HTTP 메서드", example = "POST")
        String method,
        @Schema(description = "엔드포인트", example = "/payments")
        String endpoint,
        @Schema(description = "성공 여부", example = "true")
        boolean success,
        @Schema(description = "실행 상태", example = "SUCCESS")
        ExecutionStepStatus status,
        @Schema(description = "응답 코드", example = "200")
        Integer responseCode,
        @Schema(description = "실행 시간(ms)", example = "150")
        Long durationMs,
        @Schema(description = "시작 시각", example = "2026-04-12T03:10:02")
        LocalDateTime startedAt,
        @Schema(description = "종료 시각", example = "2026-04-12T03:10:02.150")
        LocalDateTime endedAt
) {

    public static ExecutionStepLogResponse from(ExecutionStepLog log) {
        return new ExecutionStepLogResponse(
                log.getId(),
                log.getExecution().getId(),
                log.getTestCase() == null ? null : log.getTestCase().getId(),
                log.getScenarioStep() == null ? null : log.getScenarioStep().getId(),
                log.getStartedAt(),
                ExecutionViewSupport.caseName(log),
                ExecutionViewSupport.stepLabel(log),
                log.getMethod(),
                log.getPath(),
                log.getStatus() == ExecutionStepStatus.SUCCESS,
                log.getStatus(),
                log.getResponseCode(),
                log.getDurationMs(),
                log.getStartedAt(),
                log.getEndedAt()
        );
    }

}
