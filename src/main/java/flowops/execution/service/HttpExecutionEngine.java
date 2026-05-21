package flowops.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.service.ApiEndpointService;
import flowops.environment.domain.entity.Environment;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.TestValidationResult;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.repository.TestValidationResultRepository;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class HttpExecutionEngine {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final ExecutionStepLogRepository executionStepLogRepository;
    private final TestValidationResultRepository testValidationResultRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final TestCaseRepository testCaseRepository;
    private final ApiEndpointService apiEndpointService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ExecutionStepLog executeApi(Execution execution, ApiEndpoint apiEndpoint, String stepName) {
        return execute(
                execution,
                null,
                null,
                apiEndpoint,
                stepName,
                parseRequestDefinition(apiEndpoint.getRequestSchema()),
                ExpectedDefinition.success2xx()
        );
    }

    public ExecutionStepLog executeTestCase(Execution execution, TestCase testCase) {
        return executeTestCase(execution, testCase, testCase.getName());
    }

    public ExecutionStepLog executeTestCase(Execution execution, TestCase testCase, String stepName) {
        return execute(
                execution,
                testCase,
                null,
                testCase.getApiEndpoint(),
                stepName,
                parseRequestDefinition(firstText(testCase.getRequestSpec(), testCase.getApiInventory() == null ? null : testCase.getApiInventory().getRequestSchema(), testCase.getApiEndpoint().getRequestSchema())),
                parseExpectedDefinition(testCase.getExpectedSpec())
        );
    }

    public List<ExecutionStepLog> executeScenario(Execution execution, Long scenarioId) {
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId);
        Map<String, String> chainedVars = new LinkedHashMap<>();
        List<ExecutionStepLog> logs = new ArrayList<>();
        for (ScenarioStep step : steps) {
            String resolvedConfig = applyChainedVars(
                    firstText(step.getRequestConfig(),
                            step.getApiInventory() == null ? null : step.getApiInventory().getRequestSchema(),
                            step.getApiEndpoint().getRequestSchema()),
                    chainedVars
            );
            ExecutionStepLog log = execute(
                    execution,
                    null,
                    step,
                    step.getApiEndpoint(),
                    step.getLabel(),
                    parseRequestDefinition(resolvedConfig),
                    parseExpectedDefinition(step.getValidationRules())
            );
            logs.add(log);
            extractVariables(step.getExtractRules(), log.getResponseBody(), chainedVars);
        }
        return logs;
    }

    public ExecutionStepLog executeScenarioStep(Execution execution, ScenarioStep scenarioStep) {
        return executeScenarioStep(execution, scenarioStep, scenarioStep.getLabel());
    }

    public ExecutionStepLog executeScenarioStep(Execution execution, ScenarioStep scenarioStep, String stepName) {
        return execute(
                execution,
                null,
                scenarioStep,
                scenarioStep.getApiEndpoint(),
                stepName,
                parseRequestDefinition(firstText(scenarioStep.getRequestConfig(), scenarioStep.getApiInventory() == null ? null : scenarioStep.getApiInventory().getRequestSchema(), scenarioStep.getApiEndpoint().getRequestSchema())),
                parseExpectedDefinition(scenarioStep.getValidationRules())
        );
    }

    public List<ExecutionStepLog> executeApiSelection(Execution execution, List<Long> apiIds, TestLevel testLevel) {
        List<TestCase> testCases = testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(apiIds)
                .stream()
                .filter(testCase -> testCase.getTestLevel() == testLevel)
                .toList();
        if (!testCases.isEmpty()) {
            return testCases.stream()
                    .map(testCase -> executeTestCase(execution, testCase))
                    .toList();
        }
        return apiIds.stream()
                .map(apiEndpointService::getApiEndpoint)
                .map(api -> executeApi(execution, api, "API execution"))
                .toList();
    }

    public ExecutionStepLog execute(
            Execution execution,
            TestCase testCase,
            ScenarioStep scenarioStep,
            ApiEndpoint apiEndpoint,
            String stepName,
            RequestDefinition requestDefinition,
            ExpectedDefinition expectedDefinition
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        ActualHttpResult result = callHttp(execution.getEnvironment(), apiEndpoint, requestDefinition);
        LocalDateTime endedAt = LocalDateTime.now();
        boolean passed = expectedDefinition.matches(result.statusCode());
        String errorMessage = passed ? null : "Expected HTTP status " + expectedDefinition.label()
                + ", but received " + result.statusCode() + ".";

        ExecutionStepLog log = executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .testCase(testCase)
                .scenarioStep(scenarioStep)
                .stepOrder(scenarioStep == null ? null : scenarioStep.getStepOrder())
                .stepName(stepName)
                .method(apiEndpoint.getMethod().name())
                .path(apiEndpoint.getPath())
                .status(passed ? ExecutionStepStatus.SUCCESS : ExecutionStepStatus.FAILED)
                .requestBody(result.requestBody())
                .responseBody(result.responseBody())
                .responseCode(result.statusCode())
                .durationMs(Duration.between(startedAt, endedAt).toMillis())
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .createdAt(startedAt)
                .build());
        saveStatusValidation(log, expectedDefinition, result.statusCode(), passed, errorMessage);
        return log;
    }

    private ActualHttpResult callHttp(Environment environment, ApiEndpoint apiEndpoint, RequestDefinition requestDefinition) {
        try {
            URI uri = buildUri(
                    normalizeBaseUrl(environment == null ? null : environment.getBaseUrl()),
                    applyPathParams(apiEndpoint.getPath(), requestDefinition.pathParams()),
                    requestDefinition.queryParams()
            );
            WebClient.RequestBodySpec request = webClientBuilder.build()
                    .method(HttpMethod.valueOf(apiEndpoint.getMethod().name()))
                    .uri(uri)
                    .headers(headers -> {
                        headers.setAll(parseStringMap(environment == null ? null : environment.getHeaders()));
                        headers.setAll(requestDefinition.headers());
                        if (requestDefinition.hasBody()) {
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        }
                    });

            return request.exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new ActualHttpResult(
                                    response.statusCode().value(),
                                    requestDefinition.bodyAsString(),
                                    body
                            )))
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (Exception exception) {
            return new ActualHttpResult(
                    0,
                    requestDefinition.bodyAsString(),
                    "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}"
            );
        }
    }

    private URI buildUri(String baseUrl, String path, Map<String, String> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
                .path(path.startsWith("/") ? path : "/" + path);
        queryParams.forEach(builder::queryParam);
        return builder.build(true).toUri();
    }

    private String applyPathParams(String path, Map<String, String> pathParams) {
        String resolvedPath = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            resolvedPath = resolvedPath
                    .replace("{" + entry.getKey() + "}", entry.getValue())
                    .replace(":" + entry.getKey(), entry.getValue());
        }
        return resolvedPath;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private RequestDefinition parseRequestDefinition(String spec) {
        JsonNode root = parseJson(spec);
        if (root == null || root.isNull() || root.isMissingNode()) {
            return RequestDefinition.empty(objectMapper);
        }
        if (root.isObject() && root.isEmpty()) {
            return RequestDefinition.empty(objectMapper);
        }
        JsonNode body = firstPresent(root, "body", "json", "payload", "requestBody");
        if (body == null && !root.has("headers") && !root.has("queryParams") && !root.has("query") && !root.has("params")) {
            body = root;
        }
        return new RequestDefinition(
                parseStringMap(firstPresent(root, "headers")),
                parseStringMap(firstPresent(root, "pathParams", "path")),
                parseStringMap(firstPresent(root, "queryParams", "query", "params")),
                body == null || body.isNull() || body.isMissingNode() ? null : body,
                objectMapper
        );
    }

    private ExpectedDefinition parseExpectedDefinition(String spec) {
        JsonNode root = parseJson(spec);
        if (root == null || root.isNull() || root.isMissingNode()) {
            return ExpectedDefinition.success2xx();
        }
        JsonNode status = firstPresent(root, "status", "statusCode", "expectedStatusCode");
        if (status != null && status.canConvertToInt()) {
            return ExpectedDefinition.exact(status.asInt());
        }
        return ExpectedDefinition.success2xx();
    }

    private void extractVariables(String extractRules, String responseBody, Map<String, String> chainedVars) {
        if (extractRules == null || extractRules.isBlank()) return;
        JsonNode rules = parseJson(extractRules);
        JsonNode response = parseJson(responseBody);
        if (rules == null || rules.isNull() || rules.isMissingNode() || response == null) return;
        if (rules.isArray()) {
            for (JsonNode rule : rules) {
                String name = rule.path("name").asText(null);
                String jsonPath = rule.path("jsonPath").asText(null);
                if (jsonPath == null) jsonPath = rule.path("path").asText(null);
                if (name == null || jsonPath == null) continue;
                String value = extractJsonPath(response, jsonPath);
                if (value != null) chainedVars.put(name, value);
            }
        } else if (rules.isObject()) {
            String name = rules.path("name").asText(null);
            String jsonPath = rules.path("jsonPath").asText(null);
            if (jsonPath == null) jsonPath = rules.path("path").asText(null);
            if (name != null && jsonPath != null) {
                String value = extractJsonPath(response, jsonPath);
                if (value != null) chainedVars.put(name, value);
            }
        }
    }

    private String extractJsonPath(JsonNode root, String jsonPath) {
        if (jsonPath == null || root == null || root.isNull() || root.isMissingNode()) return null;
        String pointer = jsonPath.trim();
        if (pointer.startsWith("$")) {
            pointer = pointer.substring(1).replace('.', '/');
        }
        if (!pointer.startsWith("/")) {
            pointer = "/" + pointer;
        }
        JsonNode node = root.at(pointer);
        if (node.isMissingNode() || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }

    private String applyChainedVars(String template, Map<String, String> vars) {
        if (template == null || vars.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.nullNode();
        }
    }

    private JsonNode firstPresent(JsonNode root, String... fieldNames) {
        if (root == null) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = root.get(fieldName);
            if (value != null && !value.isMissingNode()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> parseStringMap(String value) {
        return parseStringMap(parseJson(value));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Map<String, String> parseStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            values.put(field.getKey(), value.isTextual() ? value.asText() : value.toString());
        }
        return values;
    }

    private void saveStatusValidation(
            ExecutionStepLog log,
            ExpectedDefinition expectedDefinition,
            int actualStatus,
            boolean passed,
            String errorMessage
    ) {
        testValidationResultRepository.save(TestValidationResult.builder()
                .executionStep(log)
                .assertionName("HTTP status")
                .expectedValue(expectedDefinition.label())
                .actualValue(String.valueOf(actualStatus))
                .passed(passed)
                .message(passed ? "Response status matched the expected criteria." : errorMessage)
                .createdAt(log.getCreatedAt())
                .build());
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record RequestDefinition(
            Map<String, String> headers,
            Map<String, String> pathParams,
            Map<String, String> queryParams,
            JsonNode body,
            ObjectMapper objectMapper
    ) {

        static RequestDefinition empty(ObjectMapper objectMapper) {
            return new RequestDefinition(Map.of(), Map.of(), Map.of(), null, objectMapper);
        }

        boolean hasBody() {
            return body != null && !body.isNull() && !body.isMissingNode();
        }

        String bodyAsString() {
            if (!hasBody()) {
                return null;
            }
            try {
                return objectMapper.writeValueAsString(body);
            } catch (Exception ignored) {
                return body.toString();
            }
        }
    }

    public record ExpectedDefinition(Integer exactStatus) {

        static ExpectedDefinition exact(int status) {
            return new ExpectedDefinition(status);
        }

        static ExpectedDefinition success2xx() {
            return new ExpectedDefinition(null);
        }

        boolean matches(int actualStatus) {
            if (exactStatus != null) {
                return actualStatus == exactStatus;
            }
            return actualStatus >= 200 && actualStatus < 300;
        }

        String label() {
            return exactStatus == null ? "2xx" : String.valueOf(exactStatus);
        }
    }

    private record ActualHttpResult(
            int statusCode,
            String requestBody,
            String responseBody
    ) {
    }
}
