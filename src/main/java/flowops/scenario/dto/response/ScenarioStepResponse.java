package flowops.scenario.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.execution.support.ResponseMetadataSupport;
import flowops.execution.support.ResponseMetadataSupport.ResponseMetadata;
import flowops.scenario.domain.entity.ScenarioStep;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScenarioStepResponse(
        @Schema(description = "Scenario step database id", example = "901")
        Long id,
        @Schema(description = "AI-generated step id. Falls back to the database id when absent.", example = "step-1")
        String stepId,
        @Schema(description = "Short step reference", example = "step_1")
        String ref,
        @Schema(description = "Step order", example = "1")
        Integer stepOrder,
        @Schema(description = "FlowOps API id", example = "10")
        Long apiId,
        @Schema(description = "Endpoint information")
        EndpointResponse endpoint,
        @Schema(description = "Step label", example = "Request payment approval")
        String label,
        @Schema(description = "Chained variables JSON")
        JsonNode chainedVariables,
        @Schema(description = "Scenario step type", example = "HAPPY_PATH")
        String type,
        @Schema(description = "Step test level context", example = "REGRESSION")
        String testLevel,
        @Schema(description = "User role context", example = "CUSTOMER")
        String userRole,
        @Schema(description = "State precondition")
        String stateCondition,
        @Schema(description = "Data variant")
        String dataVariant,
        @Schema(description = "Executable request spec JSON")
        JsonNode requestSpec,
        @Schema(description = "Expected response spec JSON")
        JsonNode expectedSpec,
        @Schema(description = "Assertion spec JSON")
        JsonNode assertionSpec,
        @Schema(description = "Whether this step is a duplicate")
        Boolean duplicate,
        @Schema(description = "Legacy execution request config JSON", example = "{\"body\":{\"productId\":1}}")
        String requestConfig,
        @Schema(description = "Legacy extraction rules JSON", example = "{\"orderId\":\"$.orderId\"}")
        String extractRules,
        @Schema(description = "Legacy validation rules JSON", example = "{\"expectedStatusCode\":201}")
        String validationRules,
        @Schema(description = "Expected success HTTP status codes", example = "[200,201]")
        List<Integer> expectedStatusCodes,
        @Schema(description = "Expected error HTTP status codes", example = "[400,401,404,409,500]")
        List<Integer> errorStatusCodes,
        @Schema(description = "Error codes extracted from response examples", example = "[\"COMMON-400\"]")
        List<String> errorCodes
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public static ScenarioStepResponse from(ScenarioStep step) {
        ResponseMetadata metadata = ResponseMetadataSupport.from(responseSchema(step), step.getValidationRules());
        JsonNode requestSpec = firstMeaningfulJson(step.getRequestSpec(), step.getRequestConfig());
        JsonNode expectedSpec = expectedSpec(step);
        JsonNode assertionSpec = assertionSpec(step);
        return new ScenarioStepResponse(
                step.getId(),
                step.getStepId() == null || step.getStepId().isBlank() ? String.valueOf(step.getId()) : step.getStepId(),
                step.getRef(),
                step.getStepOrder(),
                step.getApiInventory() == null ? step.getApiEndpoint().getId() : step.getApiInventory().getId(),
                EndpointResponse.from(
                        step.getApiEndpoint(),
                        step.getApiInventory() == null ? step.getApiEndpoint().getId() : step.getApiInventory().getId()
                ),
                step.getLabel(),
                parseJson(step.getChainedVariables()),
                step.getType(),
                step.getTestLevel(),
                step.getUserRole(),
                step.getStateCondition(),
                step.getDataVariant(),
                requestSpec,
                expectedSpec,
                assertionSpec,
                step.getDuplicate(),
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

    private static JsonNode firstMeaningfulJson(String primary, String fallback) {
        JsonNode primaryJson = parseJson(primary);
        if (isMeaningful(primaryJson)) {
            return primaryJson;
        }
        JsonNode fallbackJson = parseJson(fallback);
        return isMeaningful(fallbackJson) ? fallbackJson : null;
    }

    private static JsonNode expectedSpec(ScenarioStep step) {
        JsonNode direct = parseJson(step.getExpectedSpec());
        if (isMeaningful(direct)) {
            return direct;
        }
        JsonNode validationRules = parseJson(step.getValidationRules());
        JsonNode fromValidation = objectField(validationRules, "expectedSpec");
        if (isMeaningful(fromValidation)) {
            return fromValidation;
        }
        JsonNode expectedStatusCode = objectField(validationRules, "expectedStatusCode");
        if (isMeaningful(expectedStatusCode)) {
            return OBJECT_MAPPER.getNodeFactory().objectNode().set("statusCode", expectedStatusCode);
        }
        return null;
    }

    private static JsonNode assertionSpec(ScenarioStep step) {
        JsonNode direct = parseJson(step.getAssertionSpec());
        if (isMeaningful(direct)) {
            return direct;
        }
        JsonNode validationRules = parseJson(step.getValidationRules());
        JsonNode fromValidation = objectField(validationRules, "assertionSpec");
        if (isMeaningful(fromValidation)) {
            return fromValidation;
        }
        JsonNode assertions = objectField(validationRules, "assertions");
        if (isMeaningful(assertions)) {
            return OBJECT_MAPPER.getNodeFactory().objectNode().set("assertions", assertions);
        }
        return null;
    }

    private static JsonNode objectField(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field)) {
            return null;
        }
        return node.get(field);
    }

    private static boolean isMeaningful(JsonNode node) {
        return node != null
                && !node.isNull()
                && !node.isMissingNode()
                && !(node.isObject() && node.isEmpty())
                && !(node.isArray() && node.isEmpty())
                && !(node.isTextual() && node.asText().isBlank());
    }

    private static JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception ignored) {
            return OBJECT_MAPPER.getNodeFactory().textNode(value);
        }
    }

    public record EndpointResponse(
            @Schema(description = "API id", example = "10")
            Long id,
            @Schema(description = "HTTP method", example = "POST")
            ApiMethod method,
            @Schema(description = "API path", example = "/orders")
            String path,
            @Schema(description = "Domain tag", example = "ORDER")
            String domainTag,
            @Schema(description = "Controller name", example = "OrderController")
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
