package flowops.execution.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class ResponseMetadataSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ResponseMetadataSupport() {
    }

    public static ResponseMetadata from(String responseSchema, String expectedOrValidationSpec) {
        LinkedHashSet<Integer> expectedStatusCodes = new LinkedHashSet<>();
        LinkedHashSet<Integer> errorStatusCodes = new LinkedHashSet<>();
        LinkedHashSet<String> errorCodes = new LinkedHashSet<>();

        JsonNode responseRoot = parse(responseSchema);
        collectIntegerArray(responseRoot, "expectedStatusCodes", expectedStatusCodes);
        collectIntegerArray(responseRoot, "errorStatusCodes", errorStatusCodes);
        collectErrorCodes(responseRoot, errorCodes);

        JsonNode expectedRoot = parse(expectedOrValidationSpec);
        collectExpectedStatus(expectedRoot, expectedStatusCodes);
        collectIntegerArray(expectedRoot, "expectedStatusCodes", expectedStatusCodes);
        collectIntegerArray(expectedRoot, "errorStatusCodes", errorStatusCodes);
        collectErrorCodes(expectedRoot, errorCodes);

        return new ResponseMetadata(
                List.copyOf(expectedStatusCodes),
                List.copyOf(errorStatusCodes),
                List.copyOf(errorCodes)
        );
    }

    private static JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void collectExpectedStatus(JsonNode root, LinkedHashSet<Integer> target) {
        collectIntegerField(root, "expectedStatusCode", target);
        collectIntegerField(root, "statusCode", target);
        collectIntegerField(root, "status", target);
        JsonNode expectedSpec = root == null ? null : root.get("expectedSpec");
        if (expectedSpec != null && expectedSpec.isObject()) {
            collectExpectedStatus(expectedSpec, target);
        }
    }

    private static void collectIntegerArray(JsonNode root, String fieldName, LinkedHashSet<Integer> target) {
        JsonNode values = root == null ? null : root.get(fieldName);
        if (values == null || !values.isArray()) {
            return;
        }
        values.forEach(value -> {
            if (value.canConvertToInt()) {
                target.add(value.intValue());
            }
        });
    }

    private static void collectIntegerField(JsonNode root, String fieldName, LinkedHashSet<Integer> target) {
        JsonNode value = root == null ? null : root.get(fieldName);
        if (value != null && value.canConvertToInt()) {
            target.add(value.intValue());
        }
    }

    private static void collectErrorCodes(JsonNode root, LinkedHashSet<String> target) {
        collectTextField(root, "errorCode", target);
        collectTextField(root, "code", target);
        JsonNode responses = root == null ? null : root.get("responses");
        if (responses != null && responses.isArray()) {
            responses.forEach(response -> {
                JsonNode sampleBody = response.path("sampleBody");
                collectTextField(sampleBody, "errorCode", target);
                collectTextField(sampleBody, "code", target);
            });
        }
    }

    private static void collectTextField(JsonNode root, String fieldName, LinkedHashSet<String> target) {
        JsonNode value = root == null ? null : root.get(fieldName);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            target.add(value.asText());
        }
    }

    public record ResponseMetadata(
            List<Integer> expectedStatusCodes,
            List<Integer> errorStatusCodes,
            List<String> errorCodes
    ) {
    }
}
