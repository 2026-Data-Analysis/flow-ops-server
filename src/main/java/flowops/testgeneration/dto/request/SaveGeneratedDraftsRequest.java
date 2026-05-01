package flowops.testgeneration.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SaveGeneratedDraftsRequest(
        @ArraySchema(schema = @Schema(description = "저장할 생성 초안과 최종 테스트 케이스 입력값"))
        @NotEmpty(message = "저장할 테스트 케이스를 하나 이상 선택해야 합니다.")
        List<@Valid TestCaseDraftSaveRequest> testCases
) {

    public record TestCaseDraftSaveRequest(
            @Schema(description = "저장할 생성 초안 ID", example = "1001")
            @NotNull(message = "초안 ID는 필수입니다.")
            Long draftId,

            @Schema(description = "테스트 케이스 이름", example = "주문 생성 성공 케이스")
            @NotBlank(message = "테스트 케이스 이름은 필수입니다.")
            @Size(max = 200, message = "테스트 케이스 이름은 200자 이하여야 합니다.")
            String name,

            @Schema(description = "테스트 케이스 설명", example = "정상 주문 생성 요청 시 주문 ID가 반환되는지 검증합니다.")
            @Size(max = 4000, message = "설명은 4000자 이하여야 합니다.")
            String description,

            @Schema(description = "테스트 케이스 유형. 생략하면 AI 초안의 유형을 사용합니다.", example = "HAPPY_PATH")
            String type,

            @Schema(description = "테스트 레벨. 생략하면 환경 기본 테스트 레벨을 사용합니다.", example = "REGRESSION")
            String testLevel,

            @Schema(description = "사용자 역할", example = "CUSTOMER")
            @Size(max = 100, message = "사용자 역할은 100자 이하여야 합니다.")
            String userRole,

            @Schema(description = "사전 상태 조건", example = "로그인된 고객이며 장바구니에 상품이 담겨 있습니다.")
            @Size(max = 2000, message = "사전 상태 조건은 2000자 이하여야 합니다.")
            String stateCondition,

            @Schema(description = "데이터 변형 조건", example = "일반 상품 1개")
            @Size(max = 1000, message = "데이터 변형 조건은 1000자 이하여야 합니다.")
            String dataVariant,

            @Schema(description = "요청 명세 JSON 또는 텍스트", example = "{\"body\":{\"productId\":1,\"quantity\":1}}")
            String requestSpec,

            @Schema(description = "기대 결과 JSON 또는 텍스트", example = "{\"status\":201,\"body\":{\"orderId\":\"notNull\"}}")
            @NotBlank(message = "기대 결과는 필수입니다.")
            String expectedResult,

            @Schema(description = "검증 규칙 JSON 또는 텍스트. 생략하면 기대 결과를 기반으로 사용합니다.", example = "{\"assertions\":[\"status == 201\",\"body.orderId != null\"]}")
            String assertionSpec
    ) {
    }
}
