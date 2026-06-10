package flowops.testgeneration.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.execution.support.ExecutionRequestSpecSupport;
import flowops.execution.support.ResponseMetadataSupport;
import flowops.execution.support.ResponseMetadataSupport.ResponseMetadata;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GeneratedTestCaseDraftResponse(
        @Schema(description = "Generated draft ID", example = "1001")
        Long id,
        @Schema(description = "Generation job ID", example = "77")
        Long generationId,
        @Schema(description = "API endpoint or inventory ID", example = "10")
        Long apiId,
        @Schema(description = "Frontend display name for the selected endpoint.", example = "POST /orders")
        String endpointName,
        @Schema(description = "Selected endpoint metadata for grouping generated drafts.")
        SelectedEndpointResponse selectedEndpoint,
        @Schema(description = "Generated test case title", example = "Order creation succeeds")
        String title,
        @Schema(description = "HTTP method used for execution.", example = "POST")
        String executionMethod,
        @Schema(description = "Endpoint path used for execution.", example = "/orders")
        String executionEndpoint,
        @Schema(description = "Generated test case description")
        String description,
        @Schema(description = "Test case type", example = "HAPPY_PATH")
        String type,
        @Schema(description = "Risk/test level", example = "REGRESSION")
        String riskLevel,
        @JsonProperty("risk_level")
        @Schema(description = "Risk/test level in orchestrator response format.", example = "MEDIUM")
        String risk_level,
        @Schema(description = "User role", example = "CUSTOMER")
        String userRole,
        @Schema(description = "Precondition/state", example = "Signed in customer")
        String stateCondition,
        @Schema(description = "Data variant", example = "single product")
        String dataVariant,
        @Schema(description = "Structured request specification for frontend editors.")
        RequestSpecResponse request,
        @Schema(description = "Normalized request spec JSON", example = "{\"body\":{\"productId\":1,\"quantity\":1}}")
        String requestSpec,
        @Schema(description = "Expected result spec JSON", example = "{\"status\":201}")
        String expectedSpec,
        @Schema(description = "Structured expected result spec for frontend editors.")
        JsonNode expected,
        @Schema(description = "Assertion spec JSON", example = "{\"assertions\":[\"status == 201\"]}")
        String assertionSpec,
        @Schema(description = "Structured assertion spec for frontend editors.")
        JsonNode assertion,
        @Schema(description = "Expected HTTP status codes", example = "[200,201]")
        List<Integer> expectedStatusCodes,
        @Schema(description = "Error HTTP status codes", example = "[400,401,404,409,500]")
        List<Integer> errorStatusCodes,
        @Schema(description = "Error codes extracted from response examples", example = "[\"COMMON-400\"]")
        List<String> errorCodes,
        @Schema(description = "Duplicate candidate", example = "false")
        boolean duplicate,
        @Schema(description = "Selected for saving", example = "false")
        boolean selectedForSave,
        @Schema(description = "Draft creation timestamp", example = "2026-04-12T02:10:00")
        LocalDateTime createdAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static GeneratedTestCaseDraftResponse from(GeneratedTestCaseDraft draft) {
        ResponseMetadata metadata = ResponseMetadataSupport.from(responseSchema(draft), draft.getExpectedSpec());
        Long apiId = draft.getApiEndpoint().getId();
        RequestSpecResponse request = RequestSpecResponse.from(draft);
        String executionMethod = request.method();
        String executionEndpoint = request.endpoint();
        String riskLevel = defaultIfBlank(draft.getRiskLevel(), draft.getType());
        return new GeneratedTestCaseDraftResponse(
                draft.getId(),
                draft.getGeneration().getId(),
                apiId,
                executionMethod + " " + executionEndpoint,
                SelectedEndpointResponse.from(draft.getApiEndpoint(), apiId),
                draft.getTitle(),
                executionMethod,
                executionEndpoint,
                draft.getDescription(),
                draft.getType(),
                riskLevel,
                riskLevel,
                draft.getUserRole(),
                draft.getStateCondition(),
                draft.getDataVariant(),
                request,
                request.toStorageText(),
                draft.getExpectedSpec(),
                parse(draft.getExpectedSpec()),
                draft.getAssertionSpec(),
                parse(draft.getAssertionSpec()),
                metadata.expectedStatusCodes(),
                metadata.errorStatusCodes(),
                metadata.errorCodes(),
                draft.isDuplicate(),
                draft.isSelectedForSave(),
                draft.getCreatedAt()
        );
    }

    private static String executionMethod(GeneratedTestCaseDraft draft) {
        String override = ExecutionRequestSpecSupport.executionMethod(draft.getRequestSpec());
        return override == null ? draft.getApiEndpoint().getMethod().name() : override;
    }

    private static String executionEndpoint(GeneratedTestCaseDraft draft) {
        String override = ExecutionRequestSpecSupport.executionEndpoint(draft.getRequestSpec());
        return override == null ? draft.getApiEndpoint().getPath() : override;
    }

    private static String responseSchema(GeneratedTestCaseDraft draft) {
        if (draft.getApiInventory() != null) {
            return draft.getApiInventory().getResponseSchema();
        }
        return draft.getApiEndpoint().getResponseSchema();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SelectedEndpointResponse(
            @Schema(description = "API or inventory ID used by the generation request.", example = "10")
            Long id,
            @Schema(description = "HTTP method.", example = "POST")
            ApiMethod method,
            @Schema(description = "API path.", example = "/orders")
            String path,
            @Schema(description = "Domain tag.", example = "ORDER")
            String domainTag,
            @Schema(description = "Controller name.", example = "OrderController")
            String controllerName
    ) {
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

    public record RequestSpecResponse(
            @Schema(description = "HTTP method used for execution.", example = "POST")
            String method,
            @Schema(description = "Endpoint path used for execution.", example = "/orders")
            String endpoint,
            @Schema(description = "Request headers.")
            Map<String, String> headers,
            @Schema(description = "Path parameters.")
            Map<String, String> pathParams,
            @Schema(description = "Query parameters.")
            Map<String, String> queryParams,
            @Schema(description = "Request body.")
            JsonNode body
    ) {
        public static RequestSpecResponse from(GeneratedTestCaseDraft draft) {
            JsonNode root = parse(draft.getRequestSpec());
            return new RequestSpecResponse(
                    executionMethod(draft),
                    executionEndpoint(draft),
                    stringMap(firstPresent(root, "headers")),
                    stringMap(firstPresent(root, "pathParams", "pathParameters", "path_params", pathObject(root))),
                    stringMap(firstPresent(root, "queryParams", "queryParameters", "query_params", "query", "params")),
                    requestBody(root)
            );
        }

        private String toStorageText() {
            ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
            putText(normalized, "method", method);
            putText(normalized, "endpoint", endpoint);
            putObject(normalized, "headers", headers);
            putObject(normalized, "pathParams", pathParams);
            putObject(normalized, "queryParams", queryParams);
            if (body != null && !body.isNull() && !body.isMissingNode()) {
                normalized.set("body", body);
            }
            try {
                return OBJECT_MAPPER.writeValueAsString(normalized);
            } catch (Exception exception) {
                return normalized.toString();
            }
        }

        private static void putText(ObjectNode target, String fieldName, String value) {
            if (value != null && !value.isBlank()) {
                target.put(fieldName, value);
            }
        }

        private static void putObject(ObjectNode target, String fieldName, Map<String, String> values) {
            if (values != null && !values.isEmpty()) {
                ObjectNode object = OBJECT_MAPPER.createObjectNode();
                values.forEach(object::put);
                target.set(fieldName, object);
            }
        }
    }

    private static JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return OBJECT_MAPPER.nullNode();
        }
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception ignored) {
            return OBJECT_MAPPER.getNodeFactory().textNode(value);
        }
    }

    private static JsonNode requestBody(JsonNode root) {
        JsonNode body = firstPresent(root, "body", "json", "payload", "requestBody");
        if (body != null) {
            return body;
        }
        if (root == null || root.isNull() || root.isMissingNode()) {
            return OBJECT_MAPPER.nullNode();
        }
        if (!root.isObject()) {
            return root;
        }
        if (!hasAny(root, "headers", "queryParams", "queryParameters", "query_params", "query", "params",
                "pathParams", "pathParameters", "path_params", "endpoint", "executionEndpoint",
                "execution_endpoint", "requestPath", "request_path", "url", "method", "httpMethod", "http_method")) {
            return root;
        }
        return OBJECT_MAPPER.nullNode();
    }

    private static JsonNode firstPresent(JsonNode root, Object... fieldNamesOrNodes) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (Object fieldNameOrNode : fieldNamesOrNodes) {
            if (fieldNameOrNode instanceof JsonNode node) {
                if (node != null && !node.isMissingNode()) {
                    return node;
                }
                continue;
            }
            String fieldName = String.valueOf(fieldNameOrNode);
            if (!fieldName.isBlank() && root.has(fieldName)) {
                return root.get(fieldName);
            }
        }
        return null;
    }

    private static JsonNode pathObject(JsonNode root) {
        if (root != null && root.isObject() && root.has("path") && root.get("path").isObject()) {
            return root.get("path");
        }
        return null;
    }

    private static Map<String, String> stringMap(JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().isTextual()
                ? entry.getValue().asText()
                : entry.getValue().toString()));
        return values;
    }

    private static boolean hasAny(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (root.has(fieldName)) {
                return true;
            }
        }
        return false;
    }
}
