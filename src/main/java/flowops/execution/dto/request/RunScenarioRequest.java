package flowops.execution.dto.request;

import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RunScenarioRequest(
        @Schema(description = "앱 ID", example = "1")
        @NotNull(message = "앱 ID는 필수입니다.")
        Long appId,

        @Schema(description = "환경 ID. 비어 있으면 앱의 첫 번째 환경을 사용합니다.", example = "3")
        Long environmentId,

        @Schema(description = "실행할 시나리오 ID (단일)", example = "10")
        Long scenarioId,

        @Schema(description = "실행할 시나리오 ID 목록 (복수)", example = "[10, 11]")
        List<Long> scenarioIds,

        @Schema(description = "테스트 레벨. 비어 있으면 환경 기본 테스트 레벨을 사용합니다.", example = "REGRESSION")
        TestLevel testLevel,

        @Schema(description = "실행 요청자", example = "qa.engineer@flowops.dev")
        @NotBlank(message = "생성자는 필수입니다.")
        String createdBy,

        @Schema(description = "테스트 실행 후 생성된 데이터를 삭제하는 tearDown 모드", example = "true")
        Boolean tearDownMode
) {
}
