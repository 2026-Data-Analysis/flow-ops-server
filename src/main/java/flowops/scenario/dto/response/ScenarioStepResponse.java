package flowops.scenario.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.scenario.domain.entity.ScenarioStep;
import io.swagger.v3.oas.annotations.media.Schema;

public record ScenarioStepResponse(
        @Schema(description = "시나리오 스텝 ID", example = "901")
        Long id,
        @Schema(description = "스텝 순서", example = "1")
        Integer stepOrder,
        @Schema(description = "API ID", example = "10")
        Long apiId,
        @Schema(description = "엔드포인트 정보")
        EndpointResponse endpoint,
        @Schema(description = "스텝 라벨", example = "결제 승인 요청")
        String label,
        @Schema(description = "요청 설정 JSON", example = "{\"body\":{\"amount\":10000}}")
        String requestConfig,
        @Schema(description = "추출 규칙 JSON", example = "{\"paymentId\":\"$.paymentId\"}")
        String extractRules,
        @Schema(description = "검증 규칙 JSON", example = "{\"assertions\":[\"status == 200\"]}")
        String validationRules
) {

    public static ScenarioStepResponse from(ScenarioStep step) {
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
                step.getValidationRules()
        );
    }

    public record EndpointResponse(
            @Schema(description = "API ID", example = "10")
            Long id,
            @Schema(description = "HTTP 메서드", example = "POST")
            ApiMethod method,
            @Schema(description = "API 경로", example = "/payments")
            String path,
            @Schema(description = "도메인 태그", example = "PAYMENT")
            String domainTag,
            @Schema(description = "컨트롤러 이름", example = "PaymentController")
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
