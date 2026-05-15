package flowops.report.dto.response;

import flowops.execution.domain.entity.ExecutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IncidentDashboardResponse(
        @Schema(description = "성공률(%)", example = "84.6")
        double successRate,
        @Schema(description = "총 테스트 수", example = "120")
        int totalTests,
        @Schema(description = "실패 테스트 수", example = "18")
        int failedTests,
        @Schema(description = "평균 실행 시간(ms)", example = "183.4")
        double avgDurationMs,
        MetricChangeResponse totalErrors,
        MetricChangeResponse criticalErrors,
        MetricChangeResponse recurring,
        MetricChangeResponse failureRate,
        MetricChangeResponse mttr,
        MetricChangeResponse affectedApis,
        List<TestResultTrendPointResponse> testResultsTrend,
        List<ErrorDistributionResponse> errorDistribution,
        List<EnvironmentErrorResponse> errorsByEnvironment,
        List<TopFailingApiResponse> topFailingApis,
        List<RecentIncidentResponse> recentIncidents
) {

    public record MetricChangeResponse(
            @Schema(description = "현재 값", example = "24.0")
            double value,
            @Schema(description = "이전 기간 대비 증감률(%)", example = "12.5")
            double vsLastPeriodPercent,
            @Schema(description = "이전 기간 대비 증감값", example = "3.0")
            double vsLastPeriodValue
    ) {
    }

    public record TestResultTrendPointResponse(
            @Schema(description = "일자", example = "2026-04-29")
            LocalDate date,
            @Schema(description = "성공 테스트 수", example = "14")
            int passedTests,
            @Schema(description = "실패 테스트 수", example = "3")
            int failedTests,
            @Schema(description = "성공 API 수", example = "14")
            int passedApiCount,
            @Schema(description = "실패 API 수", example = "3")
            int failedApiCount
    ) {
    }

    public record ErrorDistributionResponse(
            @Schema(description = "에러 유형 또는 메시지", example = "Timeout")
            String label,
            @Schema(description = "발생 수", example = "6")
            long count
    ) {
    }

    public record EnvironmentErrorResponse(
            @Schema(description = "환경 ID", example = "3")
            Long environmentId,
            @Schema(description = "환경명", example = "production")
            String environmentName,
            @Schema(description = "에러 수", example = "11")
            long errorCount
    ) {
    }

    public record TopFailingApiResponse(
            @Schema(description = "환경 ID", example = "3")
            Long environmentId,
            @Schema(description = "환경명", example = "production")
            String environmentName,
            @Schema(description = "엔드포인트", example = "POST /payments")
            String endpoint,
            @Schema(description = "에러 발생 수", example = "7")
            long errorCount
    ) {
    }

    public record RecentIncidentResponse(
            @Schema(description = "실행 ID", example = "700")
            Long executionId,
            @Schema(description = "실행 시간", example = "2026-04-29T10:30:00")
            LocalDateTime executedAt,
            @Schema(description = "케이스명 또는 시나리오명", example = "POST 결제 승인 정상 흐름")
            String caseName,
            @Schema(description = "환경명", example = "production")
            String environmentName,
            @Schema(description = "상태", example = "FAILED")
            ExecutionStatus status,
            @Schema(description = "엔드포인트", example = "/payments")
            String endpoint,
            @Schema(description = "오류 메시지", example = "Database connection timeout")
            String errorMessage
    ) {
    }
}
