package flowops.testcase.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.testcase.domain.entity.TestCase;
import io.swagger.v3.oas.annotations.media.Schema;

public record TestCaseSummaryResponse(
        @Schema(description = "테스트 케이스 ID", example = "501")
        Long id,
        @Schema(description = "테스트 케이스 이름", example = "결제 승인 정상 흐름")
        String name,
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
                testCase.getName(),
                testCase.isActive(),
                testCase.getVersion(),
                SelectedEndpointResponse.from(selectedEndpoint)
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
            return new SelectedEndpointResponse(
                    endpoint.getId(),
                    endpoint.getMethod(),
                    endpoint.getPath(),
                    endpoint.getDomainTag(),
                    endpoint.getControllerName()
            );
        }
    }
}
