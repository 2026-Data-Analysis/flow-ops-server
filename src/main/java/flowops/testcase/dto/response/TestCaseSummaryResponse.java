package flowops.testcase.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record TestCaseSummaryResponse(
        @Schema(description = "테스트 케이스 ID", example = "501")
        Long id,
        @Schema(description = "API ID", example = "101")
        Long apiId,
        @Schema(description = "테스트 케이스 이름", example = "결제 승인 정상 흐름")
        String name,
        @Schema(description = "테스트케이스 유형", example = "HAPPY_PATH")
        TestCaseType type,
        @Schema(description = "테스트 레벨", example = "SMOKE")
        TestLevel testLevel,
        @Schema(description = "설명")
        String description,
        @Schema(description = "기대 결과 명세")
        String expectedSpec,
        @Schema(description = "요청 명세")
        String requestSpec,
        @Schema(description = "검증 명세")
        String assertionSpec,
        @Schema(description = "사용자 역할")
        String userRole,
        @Schema(description = "상태 조건")
        String stateCondition,
        @Schema(description = "데이터 변형 조건")
        String dataVariant,
        @Schema(description = "활성 여부", example = "true")
        boolean active,
        @Schema(description = "현재 버전", example = "3")
        Integer version,
        @Schema(description = "목록 조회 기준으로 선택된 엔드포인트")
        SelectedEndpointResponse selectedEndpoint
) {

    public static TestCaseSummaryResponse from(TestCase testCase, ApiEndpoint selectedEndpoint) {
        return new TestCaseSummaryResponse(
                testCase.getId(),
                testCase.getApiInventory() == null ? testCase.getApiEndpoint().getId() : testCase.getApiInventory().getId(),
                testCase.getName(),
                testCase.getType(),
                testCase.getTestLevel(),
                testCase.getDescription(),
                testCase.getExpectedSpec(),
                testCase.getRequestSpec(),
                testCase.getAssertionSpec(),
                testCase.getUserRole(),
                testCase.getStateCondition(),
                testCase.getDataVariant(),
                testCase.isActive(),
                testCase.getVersion(),
                SelectedEndpointResponse.from(
                        selectedEndpoint,
                        testCase.getApiInventory() == null ? selectedEndpoint.getId() : testCase.getApiInventory().getId()
                )
        );
    }

    public record SelectedEndpointResponse(
            @Schema(description = "API ID", example = "10")
            Long id,
            @Schema(description = "HTTP 메서드", example = "GET")
            ApiMethod method,
            @Schema(description = "API 경로", example = "/orders/{orderId}")
            String path,
            @Schema(description = "도메인 태그", example = "ORDER")
            String domainTag,
            @Schema(description = "컨트롤러 이름", example = "OrderController")
            String controllerName
    ) {
        public static SelectedEndpointResponse from(ApiEndpoint endpoint) {
            return from(endpoint, endpoint.getId());
        }

        public static SelectedEndpointResponse from(ApiEndpoint endpoint, Long id) {
            return new SelectedEndpointResponse(
                    id,
                    endpoint.getMethod(),
                    endpoint.getPath(),
                    endpoint.getDomainTag(),
                    endpoint.getControllerName()
            );
        }
    }
}
