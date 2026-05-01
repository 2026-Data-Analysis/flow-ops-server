package flowops.testgeneration.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateTestGenerationRequest(
        @Schema(description = "앱 ID", example = "1")
        @NotNull(message = "앱 ID는 필수입니다.")
        Long appId,

        @Schema(description = "생성 기준 환경 ID", example = "3")
        @NotNull(message = "환경 ID는 필수입니다.")
        Long environmentId,

        @Schema(description = "생성 요청자", example = "qa.lead@flowops.dev")
        @NotBlank(message = "요청자는 필수입니다.")
        String requestedBy,

        @ArraySchema(schema = @Schema(description = "테스트 케이스를 생성할 API ID", example = "10"))
        @NotEmpty(message = "API ID를 하나 이상 선택해야 합니다.")
        List<Long> selectedApiIds,

        @Schema(description = "AI 생성에 참고할 업무/테스트 컨텍스트", example = "결제 승인과 취소 API 중심으로 신규 회귀 테스트 초안을 생성")
        String contextSummary,

        @Schema(description = "현재 테스트 커버리지 비율", example = "42.5")
        Double currentCoverage
) {
}
