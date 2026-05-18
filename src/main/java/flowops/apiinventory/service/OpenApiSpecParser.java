package flowops.apiinventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import flowops.apiinventory.domain.DomainTag;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
                                domainTag(pathEntry.getKey(), operation),
                                textOrNull(operation, "summary"),
                                specVersion,
                                authRequired(operation, globalAuthRequired),
                                requestSchema(pathItem, operation),
                                responseSchema(operation)
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

    private String domainTag(String path, JsonNode operation) {
        JsonNode tags = operation.path("tags");
        if (tags.isArray() && tags.size() > 0 && tags.get(0).isTextual() && !tags.get(0).asText().isBlank()) {
            return DomainTag.normalize(tags.get(0).asText());
        }
        return DomainTag.fromPath(path);
    }

    private String requestSchema(JsonNode pathItem, JsonNode operation) {
        Map<String, Object> request = new LinkedHashMap<>();
        Map<String, Object> pathParams = new LinkedHashMap<>();
        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        collectParameters(pathItem.path("parameters"), pathParams, queryParams, headers);
        collectParameters(operation.path("parameters"), pathParams, queryParams, headers);
        if (!pathParams.isEmpty()) {
            request.put("pathParams", pathParams);
        }
        if (!queryParams.isEmpty()) {
            request.put("queryParams", queryParams);
        }
        if (!headers.isEmpty()) {
            request.put("headers", headers);
        }
        JsonNode bodySchema = firstJsonRequestBodySchema(operation.path("requestBody"));
        if (bodySchema != null) {
            request.put("body", sampleValue(bodySchema));
        }
        return writeJson(request);
    }

    private String responseSchema(JsonNode operation) {
        JsonNode responses = operation.path("responses");
        JsonNode successResponse = firstPresent(responses, "200", "201", "202", "204", "default");
        JsonNode schema = firstJsonContentSchema(successResponse);
        return schema == null ? null : writeJson(schema);
    }

    private void collectParameters(
            JsonNode parameters,
            Map<String, Object> pathParams,
            Map<String, Object> queryParams,
            Map<String, Object> headers
    ) {
        if (!parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            String name = textOrNull(parameter, "name");
            String in = textOrNull(parameter, "in");
            if (name == null || in == null) {
                continue;
            }
            Object value = sampleValue(parameter.path("schema"));
            switch (in) {
                case "path" -> pathParams.put(name, value);
                case "query" -> queryParams.put(name, value);
                case "header" -> headers.put(name, value);
                default -> {
                }
            }
        }
    }

    private JsonNode firstJsonRequestBodySchema(JsonNode requestBody) {
        return firstJsonContentSchema(requestBody);
    }

    private JsonNode firstJsonContentSchema(JsonNode node) {
        JsonNode content = node.path("content");
        JsonNode mediaType = firstPresent(content, "application/json", "*/*");
        if (mediaType == null && content.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = content.fields();
            if (fields.hasNext()) {
                mediaType = fields.next().getValue();
            }
        }
        JsonNode schema = mediaType == null ? null : mediaType.path("schema");
        return schema == null || schema.isMissingNode() || schema.isNull() ? null : schema;
    }

    private Object sampleValue(JsonNode schema) {
        if (schema == null || schema.isMissingNode() || schema.isNull()) {
            return "sample";
        }
        JsonNode example = schema.path("example");
        if (!example.isMissingNode() && !example.isNull()) {
            return jsonMapper.convertValue(example, Object.class);
        }
        JsonNode enumValues = schema.path("enum");
        if (enumValues.isArray() && enumValues.size() > 0) {
            return jsonMapper.convertValue(enumValues.get(0), Object.class);
        }
        String type = textOrNull(schema, "type");
        if ("integer".equals(type)) {
            return 1;
        }
        if ("number".equals(type)) {
            return 1.0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("array".equals(type)) {
            return List.of(sampleValue(schema.path("items")));
        }
        if ("object".equals(type) || schema.path("properties").isObject()) {
            Map<String, Object> object = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = schema.path("properties").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                object.put(field.getKey(), sampleValue(field.getValue()));
            }
            return object;
        }
        String format = textOrNull(schema, "format");
        if ("date-time".equals(format)) {
            return "2026-01-01T00:00:00";
        }
        if ("date".equals(format)) {
            return "2026-01-01";
        }
        return "sample";
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
