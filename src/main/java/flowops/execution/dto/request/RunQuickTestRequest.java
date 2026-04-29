package flowops.execution.dto.request;

import flowops.environment.domain.entity.ExecutionMode;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RunQuickTestRequest(
        @Schema(description = "실행 모드. 비어 있으면 RUN_EXISTING을 사용합니다.", example = "RUN_EXISTING")
        ExecutionMode executionMode,

        @Schema(description = "테스트 레벨. 비어 있으면 환경 기본 테스트 레벨을 사용합니다.", example = "SMOKE")
        TestLevel testLevel,

        @Schema(description = "실행 요청자", example = "qa.engineer@flowops.dev")
        @NotBlank(message = "생성자는 필수입니다.")
        String createdBy
) {
}
