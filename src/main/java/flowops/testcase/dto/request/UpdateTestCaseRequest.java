package flowops.testcase.dto.request;

import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTestCaseRequest(
        @Schema(description = "테스트 케이스 이름", example = "결제 승인 정상 흐름")
        @NotBlank(message = "테스트케이스 이름은 필수입니다.")
        @Size(max = 200, message = "테스트케이스 이름은 200자 이하여야 합니다.")
        String name,

        @Schema(description = "설명", example = "유효한 카드 정보로 결제 승인 요청 시 200 응답을 검증합니다.")
        @Size(max = 4000, message = "설명은 4000자 이하여야 합니다.")
        String description,

        @Schema(description = "테스트 유형", example = "HAPPY_PATH")
        @NotNull(message = "테스트케이스 유형은 필수입니다.")
        TestCaseType type,

        @Schema(description = "테스트 레벨", example = "REGRESSION")
        @NotNull(message = "테스트 레벨은 필수입니다.")
        TestLevel testLevel,

        @Schema(description = "사용자 역할", example = "CUSTOMER")
        String userRole,
        @Schema(description = "상태 조건", example = "로그인된 사용자")
        String stateCondition,
        @Schema(description = "데이터 변형", example = "valid-card")
        String dataVariant,
        @Schema(description = "요청 명세", example = "{\"amount\":10000,\"currency\":\"KRW\"}")
        String requestSpec,
        @Schema(description = "기대 명세", example = "{\"status\":200,\"approved\":true}")
        String expectedSpec,
        @Schema(description = "검증 명세", example = "{\"assertions\":[\"status == 200\",\"approved == true\"]}")
        String assertionSpec
) {
}
