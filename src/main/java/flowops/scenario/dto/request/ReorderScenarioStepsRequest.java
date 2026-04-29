package flowops.scenario.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderScenarioStepsRequest(
        @Schema(description = "정렬할 스텝 ID와 순서 목록")
        @NotEmpty(message = "단계 순서 정보는 필수입니다.")
        List<ScenarioStepOrderItem> steps
) {
    public record ScenarioStepOrderItem(
            @Schema(description = "시나리오 스텝 ID", example = "901")
            @NotNull(message = "시나리오 단계 ID는 필수입니다.")
            Long stepId,
            @Schema(description = "변경할 순서", example = "2")
            @NotNull(message = "단계 순서는 필수입니다.")
            Integer stepOrder
    ) {
    }
}
