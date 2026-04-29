package flowops.report.dto.request;

import flowops.report.domain.entity.TargetAudience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentReportRequest(
        @Schema(description = "인시던트 제목", example = "Database Connection Timeout")
        @NotBlank(message = "인시던트 제목은 필수입니다.")
        String incident,

        @Schema(description = "대상 독자", example = "INTERNAL_TEAM")
        @NotNull(message = "대상 독자는 필수입니다.")
        TargetAudience targetAudience,

        @Schema(description = "관련 실행 ID", example = "700")
        Long executionId,

        @Schema(description = "관련 환경 ID", example = "3")
        Long environmentId,

        @Schema(description = "추가 문맥", example = "배포 직후 DB connection pool exhaustion 의심")
        String additionalContext,

        @Schema(description = "요청자", example = "qa.lead@flowops.dev")
        @NotBlank(message = "요청자는 필수입니다.")
        String requestedBy
) {
}
