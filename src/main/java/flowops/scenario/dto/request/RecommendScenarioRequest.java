package flowops.scenario.dto.request;

import flowops.scenario.domain.entity.ScenarioType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record RecommendScenarioRequest(
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "추천 목적 또는 사용자 요청", example = "결제 승인부터 취소까지 핵심 플로우 추천")
        String goal,
        @Schema(description = "시나리오 유형", example = "HAPPY_PATH")
        ScenarioType scenarioType,
        @Schema(description = "추천 테스트 레벨", example = "REGRESSION")
        TestLevel testLevel,
        @Schema(description = "비즈니스 도메인", example = "CHECKOUT")
        String businessDomain,
        @Schema(description = "요청자", example = "qa.lead@flowops.dev")
        String requestedBy,
        @ArraySchema(schema = @Schema(description = "추천에 사용할 API ID. 비어 있으면 앱의 전체 API를 사용합니다.", example = "10"))
        List<Long> apiIds
) {
}
