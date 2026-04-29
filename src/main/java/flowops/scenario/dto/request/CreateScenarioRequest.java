package flowops.scenario.dto.request;

import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateScenarioRequest(
        @Schema(description = "앱 ID", example = "1")
        @NotNull(message = "앱 ID는 필수입니다.")
        Long appId,

        @Schema(description = "시나리오 이름", example = "결제 승인 후 취소 시나리오")
        @NotBlank(message = "시나리오 이름은 필수입니다.")
        String name,

        @Schema(description = "시나리오 설명", example = "승인 이후 취소까지 이어지는 핵심 결제 흐름")
        String description,

        @Schema(description = "시나리오 유형", example = "HAPPY_PATH")
        @NotNull(message = "시나리오 유형은 필수입니다.")
        ScenarioType type,

        @Schema(description = "추천 이유", example = "결제 도메인 핵심 비즈니스 플로우이기 때문입니다.")
        String recommendationReason,

        @Schema(description = "시나리오 출처", example = "CUSTOM")
        @NotNull(message = "시나리오 출처는 필수입니다.")
        ScenarioSource source,

        @Schema(description = "시나리오 스텝 목록")
        @Valid
        List<ScenarioStepRequest> steps
) {
}
