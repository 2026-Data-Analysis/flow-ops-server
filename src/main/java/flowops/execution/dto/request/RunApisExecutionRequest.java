package flowops.execution.dto.request;

import flowops.environment.domain.entity.ExecutionMode;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RunApisExecutionRequest(
        @Schema(description = "앱 ID", example = "1")
        @NotNull(message = "앱 ID는 필수입니다.")
        Long appId,

        @Schema(description = "환경 ID", example = "3")
        @NotNull(message = "환경 ID는 필수입니다.")
        Long environmentId,

        @ArraySchema(schema = @Schema(description = "일괄 실행/생성할 API ID", example = "10"))
        @NotEmpty(message = "실행할 API를 하나 이상 선택해야 합니다.")
        List<Long> apiIds,

        @Schema(description = "실행 모드. GENERATE_AND_RUN이면 누락 테스트 케이스를 생성한 뒤 실행합니다.", example = "GENERATE_AND_RUN")
        @NotNull(message = "실행 모드는 필수입니다.")
        ExecutionMode executionMode,

        @Schema(description = "테스트 레벨. 비어 있으면 환경 기본 테스트 레벨을 사용합니다.", example = "REGRESSION")
        TestLevel testLevel,

        @Schema(description = "실행 요청자", example = "qa.engineer@flowops.dev")
        @NotBlank(message = "생성자는 필수입니다.")
        String createdBy
) {
}
