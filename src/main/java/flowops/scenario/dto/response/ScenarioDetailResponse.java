package flowops.scenario.dto.response;

import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record ScenarioDetailResponse(
        @Schema(description = "시나리오 ID", example = "300")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "시나리오 이름", example = "결제 승인 후 취소 시나리오")
        String name,
        @Schema(description = "시나리오 유형", example = "HAPPY_PATH")
        ScenarioType type,
        @Schema(description = "정렬된 스텝 목록")
        List<ScenarioStepResponse> steps,
        @Schema(description = "생성 일시", example = "2026-04-12T02:40:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T03:00:00")
        LocalDateTime updatedAt
) {

    public static ScenarioDetailResponse of(Scenario scenario, List<ScenarioStepResponse> steps) {
        return new ScenarioDetailResponse(
                scenario.getId(),
                scenario.getApp().getId(),
                scenario.getName(),
                scenario.getType(),
                steps,
                scenario.getCreatedAt(),
                scenario.getUpdatedAt()
        );
    }
}
