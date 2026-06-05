package flowops.execution.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ExecutionRequestSpecSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ExecutionRequestSpecSupport() {
    }

    public static String executionEndpoint(String requestSpec) {
        JsonNode root = parse(requestSpec);
        if (root == null || !root.isObject()) {
            return null;
        }
        return firstText(root, "endpoint", "executionEndpoint", "execution_endpoint", "path", "requestPath", "request_path", "url");
    }

    public static String executionMethod(String requestSpec) {
        JsonNode root = parse(requestSpec);
        if (root == null || !root.isObject()) {
            return null;
        }
        String method = firstText(root, "method", "executionMethod", "execution_method", "httpMethod", "http_method");
        return method == null ? null : method.toUpperCase();
    }

    private static JsonNode parse(String requestSpec) {
        if (requestSpec == null || requestSpec.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(requestSpec);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode node = root.get(fieldName);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText().trim();
            }
        }
        return null;
    }
}
