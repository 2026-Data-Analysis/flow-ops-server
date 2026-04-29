package flowops.scenario.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScenarioStepRequest(
        @Schema(description = "기존 스텝 ID, 신규 생성 시 null", example = "901")
        Long id,

        @Schema(description = "스텝 순서", example = "1")
        @NotNull(message = "단계 순서는 필수입니다.")
        Integer stepOrder,

        @Schema(description = "호출할 API ID", example = "10")
        @NotNull(message = "API ID는 필수입니다.")
        Long apiId,

        @Schema(description = "스텝 라벨", example = "결제 승인 요청")
        @NotBlank(message = "단계 라벨은 필수입니다.")
        String label,

        @Schema(description = "요청 설정 JSON", example = "{\"body\":{\"amount\":10000}}")
        String requestConfig,
        @Schema(description = "추출 규칙 JSON", example = "{\"paymentId\":\"$.paymentId\"}")
        String extractRules,
        @Schema(description = "검증 규칙 JSON", example = "{\"assertions\":[\"status == 200\"]}")
        String validationRules
) {
}
