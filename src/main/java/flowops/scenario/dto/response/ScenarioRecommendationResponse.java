package flowops.scenario.dto.response;

import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;

public record ScenarioRecommendationResponse(
        @Schema(description = "추천 시나리오 이름", example = "Critical checkout flow")
        String name,
        @Schema(description = "추천 시나리오 유형", example = "HAPPY_PATH")
        ScenarioType type,
        @Schema(description = "추천 이유", example = "Covers a high-value multi-endpoint business path.")
        String recommendationReason
) {
}
