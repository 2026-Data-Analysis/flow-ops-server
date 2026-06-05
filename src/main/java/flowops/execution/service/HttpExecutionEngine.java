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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class HttpExecutionEngine {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final Pattern BRACE_PATH_PARAM = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern COLON_PATH_PARAM = Pattern.compile("(?<![A-Za-z0-9]):([A-Za-z][A-Za-z0-9_]*)");

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
                ExpectedDefinition.responseStatus()
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

    public long countScenarioSteps(Long scenarioId) {
        return scenarioStepRepository.countByScenarioId(scenarioId);
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
            RequestDefinition requestDefinition = parseRequestDefinition(resolvedConfig)
                    .withFallbackPathParams(chainedVars);
            ExecutionStepLog log = execute(
                    execution,
                    null,
                    step,
                    step.getApiEndpoint(),
                    step.getLabel(),
                    requestDefinition,
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
        List<ValidationOutcome> validationOutcomes = validateExpectedDefinition(
                expectedDefinition,
                result.statusCode(),
                result.responseBody()
        );
        boolean passed = validationOutcomes.stream().allMatch(ValidationOutcome::passed);
        String errorMessage = passed ? null : validationOutcomes.stream()
                .filter(outcome -> !outcome.passed())
                .map(ValidationOutcome::message)
                .findFirst()
                .orElse("Response did not match the expected criteria.");

        ExecutionStepLog log = executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .testCase(testCase)
                .scenarioStep(scenarioStep)
                .stepOrder(scenarioStep == null ? null : scenarioStep.getStepOrder())
                .stepName(stepName)
                .method(result.requestMethod())
                .path(result.requestPath())
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
        saveValidations(log, validationOutcomes);
        executeTearDownIfNeeded(execution, apiEndpoint, stepName, requestDefinition, result, endedAt);
        return log;
    }

    private void executeTearDownIfNeeded(
            Execution execution,
            ApiEndpoint apiEndpoint,
            String stepName,
            RequestDefinition requestDefinition,
            ActualHttpResult result,
            LocalDateTime createdAt
    ) {
        String executedMethod = requestDefinition.method(apiEndpoint.getMethod().name());
        if (execution == null || !execution.isTearDownMode() || !"POST".equals(executedMethod)) {
            return;
        }
        if (result.statusCode() == null || result.statusCode() < 200 || result.statusCode() >= 300) {
            return;
        }
        String createdId = extractCreatedResourceId(result.responseBody());
        if (createdId == null || createdId.isBlank()) {
            saveTearDownFailure(execution, stepName, requestDefinition.endpointPath(apiEndpoint.getPath()), "Could not resolve created resource id from response body.", createdAt);
            return;
        }
        ApiEndpoint cleanupEndpoint = apiEndpointService.findCleanupEndpoint(apiEndpoint).orElse(null);
        if (cleanupEndpoint == null) {
            saveTearDownFailure(execution, stepName, requestDefinition.endpointPath(apiEndpoint.getPath()), "Could not find a matching DELETE endpoint for tearDown.", createdAt);
            return;
        }

        RequestDefinition resolvedCreateRequest = requestDefinition.withResolvedPathParams(requestDefinition.endpointPath(apiEndpoint.getPath()));
        RequestDefinition cleanupRequest = RequestDefinition.empty(objectMapper)
                .withFallbackPathParams(resolvedCreateRequest.pathParams())
                .withFallbackPathParams(pathParamsForCreatedResource(cleanupEndpoint.getPath(), createdId));
        LocalDateTime startedAt = LocalDateTime.now();
        ActualHttpResult cleanupResult = callHttp(execution.getEnvironment(), cleanupEndpoint, cleanupRequest);
        LocalDateTime endedAt = LocalDateTime.now();
        boolean passed = cleanupResult.statusCode() != null && cleanupResult.statusCode() >= 200 && cleanupResult.statusCode() < 300;
        ExecutionStepLog cleanupLog = executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .stepName("tearDown: " + stepName)
                .method(cleanupEndpoint.getMethod().name())
                .path(cleanupResult.requestPath())
                .status(passed ? ExecutionStepStatus.SUCCESS : ExecutionStepStatus.FAILED)
                .requestBody(cleanupResult.requestBody())
                .responseBody(cleanupResult.responseBody())
                .responseCode(cleanupResult.statusCode())
                .durationMs(Duration.between(startedAt, endedAt).toMillis())
                .errorMessage(passed ? null : tearDownErrorMessage(cleanupResult.statusCode()))
                .startedAt(startedAt)
                .endedAt(endedAt)
                .createdAt(startedAt)
                .build());
        saveValidations(cleanupLog, List.of(statusValidation("2xx", cleanupResult.statusCode(), passed)));
    }

    private void saveTearDownFailure(Execution execution, String stepName, String path, String message, LocalDateTime createdAt) {
        ExecutionStepLog cleanupLog = executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .stepName("tearDown: " + stepName)
                .method("DELETE")
                .path(path)
                .status(ExecutionStepStatus.FAILED)
                .responseCode(null)
                .durationMs(0L)
                .errorMessage(message)
                .startedAt(createdAt)
                .endedAt(createdAt)
                .createdAt(createdAt)
                .build());
        saveValidations(cleanupLog, List.of(new ValidationOutcome(
                "tearDown",
                "Created resource can be cleaned up",
                "NO_CLEANUP",
                false,
                message
        )));
    }

    private String tearDownErrorMessage(Integer statusCode) {
        if (statusCode == null) {
            return "tearDown DELETE did not receive an HTTP response.";
        }
        return "tearDown DELETE expected HTTP status 2xx, but received " + statusCode + ".";
    }

    private ActualHttpResult callHttp(Environment environment, ApiEndpoint apiEndpoint, RequestDefinition requestDefinition) {
        String endpointPath = requestDefinition.endpointPath(apiEndpoint.getPath());
        String requestPath = endpointPath;
        String requestMethod = requestDefinition.method(apiEndpoint.getMethod().name());
        String requestBody = requestDefinition.bodyAsString();
        try {
            RequestDefinition resolvedDefinition = requestDefinition.withResolvedPathParams(endpointPath);
            URI uri = buildUri(
                    normalizeBaseUrl(environment == null ? null : environment.getBaseUrl()),
                    applyPathParams(endpointPath, resolvedDefinition.pathParams()),
                    resolvedDefinition.queryParams()
            );
            requestPath = requestPathWithQuery(uri);
            requestBody = resolvedDefinition.bodyAsString();
            WebClient.RequestBodySpec request = webClientBuilder.build()
                    .method(HttpMethod.valueOf(requestMethod))
                    .uri(uri)
                    .headers(headers -> {
                        headers.setAll(parseStringMap(environment == null ? null : environment.getHeaders()));
                        headers.setAll(resolvedDefinition.headers());
                        if (resolvedDefinition.hasBody()) {
                            headers.setContentType(MediaType.APPLICATION_JSON);
                        }
                    });

            RequestHeadersSpec<?> requestSpec = resolvedDefinition.hasBody()
                    ? request.bodyValue(requestBody)
                    : request;

            String finalRequestPath = requestPath;
            String finalRequestBody = requestBody;
            return requestSpec.exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new ActualHttpResult(
                                    response.statusCode().value(),
                                    requestMethod,
                                    finalRequestPath,
                                    finalRequestBody,
                                    body
                            )))
                    .block();
        } catch (Exception exception) {
            return new ActualHttpResult(
                    null,
                    requestMethod,
                    requestPath,
                    requestBody,
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

    private String requestPathWithQuery(URI uri) {
        String path = uri.getRawPath();
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return path;
        }
        return path + "?" + uri.getRawQuery();
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

    private static Set<String> pathParamNames(String path) {
        if (path == null || path.isBlank()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        Matcher braceMatcher = BRACE_PATH_PARAM.matcher(path);
        while (braceMatcher.find()) {
            names.add(braceMatcher.group(1));
        }
        Matcher colonMatcher = COLON_PATH_PARAM.matcher(path);
        while (colonMatcher.find()) {
            names.add(colonMatcher.group(1));
        }
        return names;
    }

    private static String samplePathParamValue(String name) {
        String normalized = name == null ? "" : name.toLowerCase();
        if (normalized.contains("uuid")) {
            return "00000000-0000-0000-0000-000000000001";
        }
        if (normalized.endsWith("id") || normalized.contains("_id") || normalized.contains("-id")) {
            return "1";
        }
        if (normalized.contains("date")) {
            return "2026-01-01";
        }
        return "sample";
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
        String endpointPath = parseText(firstPresent(root, "endpoint", "executionEndpoint", "execution_endpoint", "requestPath", "request_path", "url"));
        JsonNode pathNode = firstPresent(root, "path");
        if (endpointPath == null && pathNode != null && pathNode.isTextual()) {
            endpointPath = pathNode.asText();
        }
        String method = parseText(firstPresent(root, "method", "httpMethod", "http_method"));
        JsonNode body = firstPresent(root, "body", "json", "payload", "requestBody");
        if (body == null
                && !root.has("headers")
                && !root.has("queryParams")
                && !root.has("query")
                && !root.has("params")
                && endpointPath == null
                && method == null) {
            body = root;
        }
        return new RequestDefinition(
                parseStringMap(firstPresent(root, "headers")),
                parseStringMap(firstPresent(root, "pathParams", "pathParameters", "path_params", pathNode != null && pathNode.isObject() ? "path" : "")),
                parseStringMap(firstPresent(root, "queryParams", "queryParameters", "query_params", "query", "params")),
                endpointPath,
                method,
                body == null || body.isNull() || body.isMissingNode() ? null : body,
                objectMapper
        );
    }

    private ExpectedDefinition parseExpectedDefinition(String spec) {
        JsonNode root = parseJson(spec);
        if (root == null || root.isNull() || root.isMissingNode() || (root.isObject() && root.isEmpty())) {
            return ExpectedDefinition.responseStatus();
        }
        JsonNode status = firstPresent(root, "status", "statusCode", "expectedStatusCode", "expected_status_code");
        JsonNode body = firstPresent(root, "body", "responseBody", "expectedBody", "expectedResponse",
                "expected", "json", "payload", "result", "data");
        if (body == null && !root.isObject()) {
            body = root;
        }
        if (body == null && root.isObject()
                && !root.has("status") && !root.has("statusCode") && !root.has("expectedStatusCode")
                && !root.has("expected_status_code")
                && !root.has("assertions")) {
            body = root;
        }
        if (status != null && status.canConvertToInt()) {
            return new ExpectedDefinition(status.asInt(), body);
        }
        return new ExpectedDefinition(null, body);
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

    private String extractCreatedResourceId(String responseBody) {
        JsonNode response = parseResponseBody(responseBody);
        return findResourceId(response);
    }

    private String findResourceId(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            JsonNode id = node.get("id");
            if (isScalarValue(id)) {
                return scalarValue(id);
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isIdField(field.getKey()) && isScalarValue(field.getValue())) {
                    return scalarValue(field.getValue());
                }
            }
            fields = node.fields();
            while (fields.hasNext()) {
                String nested = findResourceId(fields.next().getValue());
                if (nested != null) {
                    return nested;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findResourceId(item);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private boolean isIdField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase();
        return "id".equals(normalized) || normalized.endsWith("id") || normalized.endsWith("_id") || normalized.endsWith("-id");
    }

    private boolean isScalarValue(JsonNode node) {
        return node != null && (node.isTextual() || node.isNumber() || node.isBoolean());
    }

    private String scalarValue(JsonNode node) {
        return node.isTextual() ? node.asText() : node.asText();
    }

    private Map<String, String> pathParamsForCreatedResource(String cleanupPath, String createdId) {
        Map<String, String> pathParams = new LinkedHashMap<>();
        for (String name : pathParamNames(cleanupPath)) {
            pathParams.put(name, createdId);
        }
        return pathParams;
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
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }
            JsonNode value = root.get(fieldName);
            if (value != null && !value.isMissingNode()) {
                return value;
            }
        }
        return null;
    }

    private String parseText(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (!node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
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

    private void saveValidations(ExecutionStepLog log, List<ValidationOutcome> validationOutcomes) {
        validationOutcomes.forEach(outcome -> testValidationResultRepository.save(TestValidationResult.builder()
                .executionStep(log)
                .assertionName(outcome.assertionName())
                .expectedValue(outcome.expectedValue())
                .actualValue(outcome.actualValue())
                .passed(outcome.passed())
                .message(outcome.message())
                .createdAt(log.getCreatedAt())
                .build()));
    }

    private List<ValidationOutcome> validateExpectedDefinition(
            ExpectedDefinition expectedDefinition,
            Integer actualStatus,
            String responseBody
    ) {
        List<ValidationOutcome> outcomes = new ArrayList<>();
        if (expectedDefinition.exactStatus() != null) {
            outcomes.add(statusValidation(
                    String.valueOf(expectedDefinition.exactStatus()),
                    actualStatus,
                    expectedDefinition.exactStatus().equals(actualStatus)
            ));
        }
        if (hasExpectedBody(expectedDefinition)) {
            outcomes.add(bodyValidation(responseBody, expectedDefinition.expectedBody()));
        }
        if (outcomes.isEmpty()) {
            boolean passed = actualStatus != null && actualStatus >= 200 && actualStatus < 300;
            outcomes.add(statusValidation("2xx", actualStatus, passed));
        }
        return outcomes;
    }

    private boolean hasExpectedBody(ExpectedDefinition expectedDefinition) {
        JsonNode expectedBody = expectedDefinition.expectedBody();
        return expectedBody != null && !expectedBody.isNull() && !expectedBody.isMissingNode();
    }

    private ValidationOutcome statusValidation(String expectedValue, Integer actualStatus, boolean passed) {
        String actualValue = actualStatus == null ? "NO_RESPONSE" : String.valueOf(actualStatus);
        String message = passed
                ? "Response status matched the expected criteria."
                : actualStatus == null
                ? "Expected HTTP status " + expectedValue + ", but no HTTP response was received."
                : "Expected HTTP status " + expectedValue + ", but received " + actualStatus + ".";
        return new ValidationOutcome("HTTP status", expectedValue, actualValue, passed, message);
    }

    private ValidationOutcome bodyValidation(String responseBody, JsonNode expectedBody) {
        JsonNode actualBody = parseResponseBody(responseBody);
        boolean passed = jsonContains(actualBody, expectedBody);
        String message = passed
                ? "Response body matched the expected criteria."
                : "Expected response body to contain " + compactJson(expectedBody) + ".";
        return new ValidationOutcome(
                "Response body",
                compactJson(expectedBody),
                compactJson(actualBody),
                passed,
                message
        );
    }

    private JsonNode parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(responseBody);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(responseBody);
        }
    }

    private boolean jsonContains(JsonNode actual, JsonNode expected) {
        if (expected == null || expected.isNull() || expected.isMissingNode()) {
            return true;
        }
        if (actual == null || actual.isNull() || actual.isMissingNode()) {
            return false;
        }
        if (expected.isObject()) {
            if (!actual.isObject()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!jsonContains(actual.get(field.getKey()), field.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray()) {
            return actual.equals(expected);
        }
        return actual.equals(expected);
    }

    private String compactJson(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
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
            String endpointPath,
            String method,
            JsonNode body,
            ObjectMapper objectMapper
    ) {

        static RequestDefinition empty(ObjectMapper objectMapper) {
            return new RequestDefinition(Map.of(), Map.of(), Map.of(), null, null, null, objectMapper);
        }

        RequestDefinition withFallbackPathParams(Map<String, String> fallbackPathParams) {
            if (fallbackPathParams == null || fallbackPathParams.isEmpty()) {
                return this;
            }
            Map<String, String> resolvedPathParams = new LinkedHashMap<>(fallbackPathParams);
            resolvedPathParams.putAll(pathParams);
            return new RequestDefinition(headers, resolvedPathParams, queryParams, endpointPath, method, body, objectMapper);
        }

        RequestDefinition withResolvedPathParams(String endpointPath) {
            Set<String> requiredPathParams = pathParamNames(endpointPath);
            if (requiredPathParams.isEmpty()) {
                return this;
            }
            Map<String, String> resolvedPathParams = new LinkedHashMap<>(pathParams);
            Map<String, String> resolvedQueryParams = new LinkedHashMap<>(queryParams);
            for (String name : requiredPathParams) {
                String value = resolvedPathParams.get(name);
                if ((value == null || value.isBlank()) && resolvedQueryParams.containsKey(name)) {
                    value = resolvedQueryParams.remove(name);
                } else {
                    resolvedQueryParams.remove(name);
                }
                if (value == null || value.isBlank()) {
                    value = samplePathParamValue(name);
                }
                resolvedPathParams.put(name, value);
            }
            return new RequestDefinition(headers, resolvedPathParams, resolvedQueryParams, endpointPath, method, body, objectMapper);
        }

        String endpointPath(String fallbackPath) {
            return endpointPath == null || endpointPath.isBlank() ? fallbackPath : endpointPath;
        }

        String method(String fallbackMethod) {
            return method == null || method.isBlank() ? fallbackMethod : method.trim().toUpperCase();
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

    public record ExpectedDefinition(Integer exactStatus, JsonNode expectedBody) {

        static ExpectedDefinition exact(int status) {
            return new ExpectedDefinition(status, null);
        }

        static ExpectedDefinition responseStatus() {
            return new ExpectedDefinition(null, null);
        }

    }

    private record ValidationOutcome(
            String assertionName,
            String expectedValue,
            String actualValue,
            boolean passed,
            String message
    ) {
    }

    private record ActualHttpResult(
            Integer statusCode,
            String requestMethod,
            String requestPath,
            String requestBody,
            String responseBody
    ) {
    }
}
