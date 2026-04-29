package flowops.testcase.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateTestCaseActiveRequest(
        @Schema(description = "테스트 케이스 활성 여부", example = "true")
        @NotNull(message = "활성 여부는 필수입니다.")
        Boolean active
) {
}
