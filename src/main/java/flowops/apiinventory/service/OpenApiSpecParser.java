package flowops.apiinventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * OpenAPI 3.x와 Swagger 2.0 문서의 paths 영역을 내부 API 목록으로 변환합니다.
 */
@Component
public class OpenApiSpecParser {

    private static final List<String> HTTP_METHODS = List.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    public OpenApiSpecParser(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public ParsedOpenApiSpec parse(String fileName, String content) {
        try {
            JsonNode root = objectMapperFor(fileName).readTree(content);
            JsonNode paths = root.path("paths");
            if (!paths.isObject()) {
                return ParsedOpenApiSpec.empty();
            }

            String toolName = specToolName(root);
            String specVersion = specVersion(root);
            boolean globalAuthRequired = hasAuthRequirement(root.path("security"));
            List<ParsedApiOperation> operations = new ArrayList<>();
            // paths.{path}.{method} 구조를 순회해 실제 호출 가능한 API 단위로 평탄화합니다.
            Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
            while (pathFields.hasNext()) {
                Map.Entry<String, JsonNode> pathEntry = pathFields.next();
                JsonNode pathItem = pathEntry.getValue();
                for (String methodName : HTTP_METHODS) {
                    JsonNode operation = pathItem.path(methodName);
                    if (operation.isObject()) {
                        operations.add(new ParsedApiOperation(
                                ApiHttpMethod.valueOf(methodName.toUpperCase(Locale.ROOT)),
                                pathEntry.getKey(),
                                textOrNull(operation, "operationId"),
                                textOrNull(operation, "summary"),
                                specVersion,
                                authRequired(operation, globalAuthRequired)
                        ));
                    }
                }
            }
            return new ParsedOpenApiSpec(toolName, specVersion, operations);
        } catch (Exception ignored) {
            return ParsedOpenApiSpec.empty();
        }
    }

    private ObjectMapper objectMapperFor(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".yaml") || normalized.endsWith(".yml") ? yamlMapper : jsonMapper;
    }

    private String specVersion(JsonNode root) {
        String openApi = textOrNull(root, "openapi");
        if (openApi != null) {
            return openApi;
        }
        return textOrNull(root, "swagger");
    }

    private String specToolName(JsonNode root) {
        if (textOrNull(root, "openapi") != null) {
            return "OpenAPI";
        }
        if (textOrNull(root, "swagger") != null) {
            return "Swagger";
        }
        return null;
    }

    private boolean authRequired(JsonNode operation, boolean globalAuthRequired) {
        if (operation.has("security")) {
            return hasAuthRequirement(operation.path("security"));
        }
        return globalAuthRequired;
    }

    private boolean hasAuthRequirement(JsonNode securityNode) {
        return securityNode.isArray() && securityNode.size() > 0;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
