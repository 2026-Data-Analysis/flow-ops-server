package flowops.scenario.dto.request;

import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateScenarioRequest(
        @Schema(description = "시나리오 이름", example = "결제 승인 후 취소 시나리오")
        @NotBlank(message = "시나리오 이름은 필수입니다.")
        String name,

        @Schema(description = "시나리오 설명", example = "승인 이후 취소까지 이어지는 핵심 결제 흐름")
        String description,

        @Schema(description = "시나리오 유형", example = "FAILURE_RECOVERY")
        @NotNull(message = "시나리오 유형은 필수입니다.")
        ScenarioType type,

        @Schema(description = "추천 이유", example = "실패 복구 흐름까지 포함하도록 확장합니다.")
        String recommendationReason,

        @Schema(description = "시나리오 스텝 목록")
        @Valid
        List<ScenarioStepRequest> steps
) {
}
