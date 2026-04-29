package flowops.scenario.dto.response;

import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ScenarioSummaryResponse(
        @Schema(description = "시나리오 ID", example = "300")
        Long id,
        @Schema(description = "시나리오 이름", example = "결제 승인 후 취소 시나리오")
        String name,
        @Schema(description = "시나리오 설명", example = "승인 이후 취소까지 이어지는 결제 흐름")
        String description,
        @Schema(description = "시나리오 유형", example = "HAPPY_PATH")
        ScenarioType type,
        @Schema(description = "포함된 단계/테스트 케이스 수", example = "4")
        long steps,
        @Schema(description = "수정 일시", example = "2026-04-12T03:00:00")
        LocalDateTime updatedAt
) {

    public static ScenarioSummaryResponse from(Scenario scenario, long steps) {
        return new ScenarioSummaryResponse(
                scenario.getId(),
                scenario.getName(),
                scenario.getDescription(),
                scenario.getType(),
                steps,
                scenario.getUpdatedAt()
        );
    }
}
