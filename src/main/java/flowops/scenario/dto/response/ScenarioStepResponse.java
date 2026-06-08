package flowops.scenario.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.execution.support.ResponseMetadataSupport;
import flowops.execution.support.ResponseMetadataSupport.ResponseMetadata;
import flowops.scenario.domain.entity.ScenarioStep;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScenarioStepResponse(
        @Schema(description = "시나리오 스텝 ID", example = "901")
        Long id,
        @Schema(description = "스텝 순서", example = "1")
        Integer stepOrder,
        @Schema(description = "API ID", example = "10")
        Long apiId,
        @Schema(description = "엔드포인트 정보")
        EndpointResponse endpoint,
        @Schema(description = "스텝 라벨", example = "주문 생성")
        String label,
        @Schema(description = "요청 설정 JSON", example = "{\"body\":{\"productId\":1}}")
        String requestConfig,
        @Schema(description = "추출 규칙 JSON", example = "{\"orderId\":\"$.orderId\"}")
        String extractRules,
        @Schema(description = "검증 규칙 JSON", example = "{\"expectedStatusCode\":201}")
        String validationRules,
        @Schema(description = "성공 응답으로 기대할 수 있는 HTTP 상태 코드 목록", example = "[200,201]")
        List<Integer> expectedStatusCodes,
        @Schema(description = "오류 응답으로 발생할 수 있는 HTTP 상태 코드 목록", example = "[400,401,404,409,500]")
        List<Integer> errorStatusCodes,
        @Schema(description = "응답 예시에서 추출한 오류 코드 목록", example = "[\"COMMON-400\"]")
        List<String> errorCodes
) {

    public static ScenarioStepResponse from(ScenarioStep step) {
        ResponseMetadata metadata = ResponseMetadataSupport.from(responseSchema(step), step.getValidationRules());
        return new ScenarioStepResponse(
                step.getId(),
                step.getStepOrder(),
                step.getApiInventory() == null ? step.getApiEndpoint().getId() : step.getApiInventory().getId(),
                EndpointResponse.from(
                        step.getApiEndpoint(),
                        step.getApiInventory() == null ? step.getApiEndpoint().getId() : step.getApiInventory().getId()
                ),
                step.getLabel(),
                step.getRequestConfig(),
                step.getExtractRules(),
                step.getValidationRules(),
                metadata.expectedStatusCodes(),
                metadata.errorStatusCodes(),
                metadata.errorCodes()
        );
    }

    private static String responseSchema(ScenarioStep step) {
        if (step.getApiInventory() != null) {
            return step.getApiInventory().getResponseSchema();
        }
        return step.getApiEndpoint().getResponseSchema();
    }

    public record EndpointResponse(
            @Schema(description = "API ID", example = "10")
            Long id,
            @Schema(description = "HTTP 메서드", example = "POST")
            ApiMethod method,
            @Schema(description = "API 경로", example = "/orders")
            String path,
            @Schema(description = "도메인 태그", example = "ORDER")
            String domainTag,
            @Schema(description = "컨트롤러 이름", example = "OrderController")
            String controllerName
    ) {
        public static EndpointResponse from(ApiEndpoint endpoint) {
            return from(endpoint, endpoint.getId());
        }

        public static EndpointResponse from(ApiEndpoint endpoint, Long id) {
            return new EndpointResponse(
                    id,
                    endpoint.getMethod(),
                    endpoint.getPath(),
                    endpoint.getDomainTag(),
                    endpoint.getControllerName()
            );
        }
    }
}
