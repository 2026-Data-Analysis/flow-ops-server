package flowops.testcase.dto.request;

import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTestCaseRequest(
        @Schema(description = "API 인벤토리 ID (선택)", example = "1")
        Long apiInventoryId,

        @Schema(description = "API 엔드포인트 ID (선택)", example = "1")
        Long apiEndpointId,

        @Schema(description = "테스트 케이스 이름", example = "결제 승인 정상 흐름")
        @NotBlank(message = "테스트케이스 이름은 필수입니다.")
        @Size(max = 200)
        String name,

        @Schema(description = "설명")
        @Size(max = 4000)
        String description,

        @Schema(description = "테스트 유형", example = "HAPPY_PATH")
        @NotNull(message = "테스트케이스 유형은 필수입니다.")
        TestCaseType type,

        @Schema(description = "테스트 레벨", example = "REGRESSION")
        TestLevel testLevel,

        @Schema(description = "사용자 역할")
        String userRole,

        @Schema(description = "상태 조건")
        String stateCondition,

        @Schema(description = "데이터 변형")
        String dataVariant,

        @Schema(description = "요청 명세")
        String requestSpec,

        @Schema(description = "기대 명세")
        String expectedSpec,

        @Schema(description = "검증 명세")
        String assertionSpec
) {
}
