package flowops.report.dto.response;

import flowops.report.domain.entity.Report;
import flowops.report.domain.entity.ReportType;
import flowops.report.domain.entity.TargetAudience;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record ReportResponse(
        @Schema(description = "리포트 ID", example = "10")
        Long id,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "실행 ID", example = "700")
        Long executionId,
        @Schema(description = "리포트 타입", example = "INCIDENT")
        ReportType type,
        @Schema(description = "리포트 제목", example = "[Internal Team] Database Connection Timeout")
        String title,
        @Schema(description = "인시던트 제목", example = "Database Connection Timeout")
        String incident,
        @Schema(description = "대상 독자", example = "INTERNAL_TEAM")
        TargetAudience targetAudience,
        @Schema(description = "요약", example = "프로덕션 환경에서 데이터베이스 연결 타임아웃이 감지되었습니다.")
        String summary,
        @Schema(description = "영향 범위", example = "일부 요청이 500 에러로 실패했고 응답 시간이 증가했습니다.")
        String impact,
        @Schema(description = "권장 조치")
        List<String> nextActions,
        @Schema(description = "권장 공유 채널")
        List<String> recommendedChannels,
        @Schema(description = "연결 환경명", example = "production")
        String environmentName,
        @Schema(description = "생성 시각", example = "2026-04-29T10:30:00")
        LocalDateTime createdAt
) {

    public static ReportResponse from(Report report, IncidentReportPayload payload) {
        return new ReportResponse(
                report.getId(),
                report.getProject().getId(),
                report.getExecution() == null ? null : report.getExecution().getId(),
                report.getType(),
                report.getTitle(),
                payload.incident(),
                payload.targetAudience(),
                payload.summary(),
                payload.impact(),
                payload.nextActions(),
                payload.recommendedChannels(),
                payload.environmentName(),
                report.getCreatedAt()
        );
    }

    public record IncidentReportPayload(
            String incident,
            TargetAudience targetAudience,
            String summary,
            String impact,
            List<String> nextActions,
            List<String> recommendedChannels,
            String environmentName,
            String additionalContext
    ) {
    }
}
