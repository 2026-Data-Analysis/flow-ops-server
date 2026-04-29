package flowops.execution.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GenerateFailureTestCasesRequest(
        @Schema(description = "특정 실패 로그 ID. 없으면 실행의 첫 실패 로그를 사용합니다.", example = "8001")
        Long failedLogId,

        @Schema(description = "생성 요청자", example = "qa.lead@flowops.dev")
        @NotBlank(message = "요청자는 필수입니다.")
        String requestedBy,

        @Schema(description = "현재 커버리지", example = "42.5")
        Double currentCoverage
) {
}
