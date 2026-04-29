package flowops.execution.dto.request;

import flowops.environment.domain.entity.ExecutionMode;
import flowops.execution.domain.entity.ExecutionTriggerSource;
import flowops.execution.domain.entity.ExecutionType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExecutionRequest(
        @Schema(description = "앱 ID", example = "1")
        @NotNull(message = "앱 ID는 필수입니다.")
        Long appId,

        @Schema(description = "환경 ID", example = "3")
        @NotNull(message = "환경 ID는 필수입니다.")
        Long environmentId,

        @Schema(description = "실행 대상", example = "SCENARIO")
        @NotNull(message = "실행 유형은 필수입니다.")
        ExecutionType executionType,

        @Schema(description = "실행 대상 ID", example = "300")
        @NotNull(message = "대상 ID는 필수입니다.")
        Long targetId,

        @Schema(description = "트리거 소스", example = "MANUAL")
        @NotNull(message = "트리거 출처는 필수입니다.")
        ExecutionTriggerSource triggerSource,

        @Schema(description = "실행 모드", example = "RUN_EXISTING")
        @NotNull(message = "실행 모드는 필수입니다.")
        ExecutionMode executionMode,

        @Schema(description = "테스트 레벨. 비어 있으면 환경 기본 테스트 레벨을 사용합니다.", example = "REGRESSION")
        TestLevel testLevel,

        @Schema(description = "실행 요청자", example = "qa.engineer@flowops.dev")
        @NotBlank(message = "생성자는 필수입니다.")
        String createdBy
) {
}
