package flowops.scenario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioStepPayload;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.RecommendScenarioRequest;
import flowops.scenario.dto.request.ReorderScenarioStepsRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.request.UpdateScenarioRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioRecommendationResponse;
import flowops.scenario.dto.response.ScenarioStepResponse;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.repository.TestCaseRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final AppService appService;
    private final ApiEndpointService apiEndpointService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiInventoryRepository apiInventoryRepository;
    private final AiClient aiClient;
    private final ExternalServiceProperties externalServiceProperties;
    private final ObjectMapper objectMapper;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public ScenarioDetailResponse create(CreateScenarioRequest request) {
        log.info("Creating scenario. appId={}, name={}, type={}, source={}, stepCount={}, stepApiIds={}",
                request.appId(),
                request.name(),
                request.type(),
                request.source(),
                request.steps() == null ? 0 : request.steps().size(),
                request.steps() == null ? List.of() : request.steps().stream().map(ScenarioStepRequest::apiId).toList());
        App app = appService.getApp(request.appId());
        try {
            Scenario scenario = scenarioRepository.save(Scenario.builder()
                    .app(app)
                    .environment(null)
                    .name(request.name())
                    .description(request.description())
                    .type(request.type())
                    .recommendationReason(request.recommendationReason())
                    .source(request.source())
                    .build());
            List<ScenarioStepResponse> steps = replaceSteps(scenario, request.steps());
            log.info("Scenario created. scenarioId={}, appId={}, stepCount={}",
                    scenario.getId(),
                    app.getId(),
                    steps.size());
            return ScenarioDetailResponse.of(scenario, steps);
        } catch (RuntimeException exception) {
            log.warn("Scenario creation failed. appId={}, name={}, stepCount={}, errorType={}, error={}",
                    request.appId(),
                    request.name(),
                    request.steps() == null ? 0 : request.steps().size(),
                    rootCause(exception).getClass().getName(),
                    compact(rootCause(exception).getMessage()));
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ScenarioSummaryResponse> listByApp(Long appId, Long environmentId, Long repositoryId, String branchName) {
        appService.getApp(appId);
        List<Scenario> scenarios = scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId);
        return scenarios.stream()
                .map(scenario -> ScenarioSummaryResponse.from(
                        scenario,
                        scenarioStepRepository.countByScenarioId(scenario.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioDetailResponse getDetail(Long scenarioId) {
        Scenario scenario = getScenario(scenarioId);
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional
    public ScenarioDetailResponse update(Long scenarioId, UpdateScenarioRequest request) {
        Scenario scenario = getScenario(scenarioId);
        scenario.update(
                request.name(),
                request.description(),
                request.type(),
                request.recommendationReason()
        );
        if (request.steps() != null) {
            scenarioStepRepository.deleteAll(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId));
            return ScenarioDetailResponse.of(scenario, replaceSteps(scenario, request.steps()));
        }
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional
    public ScenarioDetailResponse reorderSteps(Long scenarioId, ReorderScenarioStepsRequest request) {
        Scenario scenario = getScenario(scenarioId);
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId);
        for (ReorderScenarioStepsRequest.ScenarioStepOrderItem item : request.steps()) {
            ScenarioStep step = steps.stream()
                    .filter(candidate -> candidate.getId().equals(item.stepId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Scenario step not found."));
            step.update(
                    item.stepOrder(),
                    step.getApiEndpoint(),
                    step.getLabel(),
                    step.getRequestConfig(),
                    step.getExtractRules(),
                    step.getValidationRules()
            );
        }
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional(readOnly = true)
    public List<ScenarioRecommendationResponse> recommend(RecommendScenarioRequest request) {
        if (externalServiceProperties.ai().mockEnabled()) {
            log.warn("Scenario recommendation returned mock data because external.ai.mock-enabled=true");
            return mockRecommendations();
        }
        if (request == null || request.appId() == null) {
            log.warn("Scenario recommendation returned mock data because request or appId is missing. requestPresent={}, appId={}",
                    request != null,
                    request == null ? null : request.appId());
            return mockRecommendations();
        }

        App app = appService.getApp(request.appId());
        List<ApiInventory> inventories = scenarioInventories(app.getId(), request.apiIds());
        List<ApiEndpoint> endpoints = inventories.isEmpty() ? scenarioEndpoints(app.getId(), request.apiIds()) : List.of();
        List<ScenarioEndpointPayload> aiEndpoints = !inventories.isEmpty()
                ? inventories.stream().map(this::toScenarioEndpointPayload).toList()
                : endpoints.stream().map(this::toScenarioEndpointPayload).toList();
        Map<String, Long> apiIdByEndpointId = scenarioApiIdByEndpointId(inventories, endpoints);
        String projectId = projectId(app, inventories);
        log.info("Scenario recommendation payload prepared. appId={}, requestedApiIdCount={}, inventoryCount={}, endpointFallbackCount={}, aiEndpointCount={}, firstEndpointIds={}",
                app.getId(),
                request.apiIds() == null ? 0 : request.apiIds().size(),
                inventories.size(),
                endpoints.size(),
                aiEndpoints.size(),
                aiEndpoints.stream().limit(5).map(ScenarioEndpointPayload::endpoint_id).toList());

        log.info("Calling AI scenario generator. appId={}, projectId={}, mode={}, apiCount={}, requestedBy={}, mockEnabled={}",
                app.getId(),
                projectId,
                scenarioMode(request),
                aiEndpoints.size(),
                request.requestedBy(),
                externalServiceProperties.ai().mockEnabled());

        ScenarioGenerateResponse response = aiClient.buildScenario(new ScenarioGenerateRequest(
                projectId,
                scenarioMode(request),
                userIntent(request),
                new ScenarioApiInventoryPayload(projectId, aiEndpoints),
                existingTestCases(inventories, endpoints),
                3,
                8
        ));

        if (response == null || !response.success() || response.data() == null || response.data().scenarios() == null) {
            log.warn("AI scenario generator returned no scenarios. appId={}, success={}, errorCode={}, errorMessage={}, traceId={}",
                    app.getId(),
                    response == null ? null : response.success(),
                    response == null ? null : response.error_code(),
                    response == null ? null : response.error_message(),
                    response == null ? null : response.trace_id());
            return List.of();
        }

        log.info("AI scenario generator raw result. appId={}, traceId={}, success={}, usedEndpointIds={}, scenarioSummaries={}",
                app.getId(),
                response.trace_id(),
                response.success(),
                response.data().used_endpoint_ids(),
                scenarioSummaries(response.data().scenarios()));

        List<ScenarioRecommendationResponse> recommendations = response.data().scenarios().stream()
                .filter(scenario -> scenario.name() != null && !scenario.name().isBlank())
                .map(scenario -> toRecommendation(scenario, request.scenarioType(), apiIdByEndpointId))
                .toList();
        long unresolvedStepCount = recommendations.stream()
                .flatMap(recommendation -> recommendation.steps().stream())
                .filter(step -> step.apiId() == null)
                .count();
        log.info("AI scenario generator completed. appId={}, scenarioCount={}, totalStepCount={}, zeroStepScenarioCount={}, traceId={}",
                app.getId(),
                recommendations.size(),
                recommendations.stream().map(ScenarioRecommendationResponse::steps).filter(java.util.Objects::nonNull).mapToInt(List::size).sum(),
                recommendations.stream().filter(recommendation -> recommendation.steps() == null || recommendation.steps().isEmpty()).count(),
                response.trace_id());
        if (unresolvedStepCount > 0) {
            log.warn("AI scenario generator returned steps with unresolved endpoint ids. appId={}, unresolvedStepCount={}, knownEndpointIds={}",
                    app.getId(),
                    unresolvedStepCount,
                    apiIdByEndpointId.keySet());
        }
        return recommendations;
    }

    private List<ScenarioRecommendationResponse> mockRecommendations() {
        return List.of(
                new ScenarioRecommendationResponse("Critical checkout flow", ScenarioType.HAPPY_PATH, "Covers a high-value multi-endpoint business path.", List.of()),
                new ScenarioRecommendationResponse("Validation guard rails", ScenarioType.EDGE_CASE, "Focuses on required-field and malformed-input failures.", List.of()),
                new ScenarioRecommendationResponse("Recovery after dependency failure", ScenarioType.FAILURE_RECOVERY, "Models retry and fallback behavior after an upstream error.", List.of())
        );
    }

    private List<ApiInventory> scenarioInventories(Long appId, List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            List<ApiInventory> inventories = apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(appId);
            log.info("Resolved scenario API inventory by app. appId={}, inventoryCount={}", appId, inventories.size());
            return inventories;
        }
        List<ApiInventory> inventories = apiIds.stream()
                .map(apiInventoryRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(inventory -> inventory.getRepositoryInfo() != null
                        && inventory.getRepositoryInfo().getApp() != null
                        && inventory.getRepositoryInfo().getApp().getId().equals(appId))
                .toList();
        log.info("Resolved scenario API inventory by requested ids. appId={}, requestedApiIdCount={}, matchedInventoryCount={}, firstRequestedApiIds={}, firstMatchedInventoryIds={}",
                appId,
                apiIds.size(),
                inventories.size(),
                apiIds.stream().limit(10).toList(),
                inventories.stream().limit(10).map(ApiInventory::getId).toList());
        if (inventories.size() != apiIds.size()) {
            log.warn("Some requested apiIds were not resolved as ApiInventory ids for app. appId={}, requestedApiIdCount={}, matchedInventoryCount={}",
                    appId,
                    apiIds.size(),
                    inventories.size());
        }
        return inventories.size() == apiIds.size() ? inventories : List.of();
    }

    private List<ApiEndpoint> scenarioEndpoints(Long appId, List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByAppId(appId);
            log.info("Resolved scenario API endpoints by app fallback. appId={}, endpointCount={}", appId, endpoints.size());
            return endpoints;
        }
        List<ApiEndpoint> endpoints = apiIds.stream()
                .map(apiEndpointService::getApiEndpoint)
                .filter(endpoint -> endpoint.getApp().getId().equals(appId))
                .toList();
        log.info("Resolved scenario API endpoints by requested ids fallback. appId={}, requestedApiIdCount={}, endpointCount={}",
                appId,
                apiIds.size(),
                endpoints.size());
        return endpoints;
    }

    private String projectId(App app, List<ApiInventory> inventories) {
        return inventories.stream()
                .findFirst()
                .map(ApiInventory::getProject)
                .map(project -> "project-" + project.getId())
                .orElse("app-" + app.getId());
    }

    private String scenarioMode(RecommendScenarioRequest request) {
        return request.goal() == null || request.goal().isBlank() ? "RECOMMEND" : "NATURAL_LANGUAGE";
    }

    private String userIntent(RecommendScenarioRequest request) {
        if (request.goal() != null && !request.goal().isBlank()) {
            return request.goal();
        }
        return "Recommend end-to-end scenarios from backend API inventory.";
    }

    private ScenarioEndpointPayload toScenarioEndpointPayload(ApiInventory inventory) {
        return new ScenarioEndpointPayload(
                endpointId(inventory.getMethod().name(), inventory.getEndpointPath()),
                inventory.getEndpointPath(),
                inventory.getMethod().name(),
                inventory.getSummary(),
                inventory.getOperationId(),
                List.of(),
                parseJson(inventory.getRequestSchema()),
                parseJson(inventory.getResponseSchema()),
                authPayload(inventory.isAuthRequired()),
                tags(inventory.getDomainTag())
        );
    }

    private ScenarioEndpointPayload toScenarioEndpointPayload(ApiEndpoint endpoint) {
        return new ScenarioEndpointPayload(
                endpointId(endpoint.getMethod().name(), endpoint.getPath()),
                endpoint.getPath(),
                endpoint.getMethod().name(),
                endpoint.getControllerName(),
                endpoint.getDomainTag(),
                List.of(),
                parseJson(endpoint.getRequestSchema()),
                parseJson(endpoint.getResponseSchema()),
                null,
                tags(endpoint.getDomainTag())
        );
    }

    private ScenarioAuthPayload authPayload(boolean authRequired) {
        return authRequired ? new ScenarioAuthPayload("bearer", "header") : new ScenarioAuthPayload("none", null);
    }

    private List<String> tags(String tag) {
        return tag == null || tag.isBlank() ? List.of() : List.of(tag);
    }

    private String endpointId(String method, String path) {
        return method + ":" + path;
    }

    private List<ScenarioExistingTestCasePayload> existingTestCases(List<ApiInventory> inventories, List<ApiEndpoint> endpoints) {
        List<TestCase> testCases = new ArrayList<>();
        if (!inventories.isEmpty()) {
            inventories.forEach(inventory -> testCases.addAll(testCaseRepository.findTop3ByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(inventory.getId())));
        } else if (!endpoints.isEmpty()) {
            testCases.addAll(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(
                    endpoints.stream().map(ApiEndpoint::getId).toList()
            ));
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        return testCases.stream()
                .filter(testCase -> seen.add(testCase.getId()))
                .limit(20)
                .map(this::toScenarioExistingTestCasePayload)
                .toList();
    }

    private ScenarioExistingTestCasePayload toScenarioExistingTestCasePayload(TestCase testCase) {
        ApiInventory inventory = testCase.getApiInventory();
        ApiEndpoint endpoint = testCase.getApiEndpoint();
        String endpointId = inventory == null
                ? endpointId(endpoint.getMethod().name(), endpoint.getPath())
                : endpointId(inventory.getMethod().name(), inventory.getEndpointPath());
        return new ScenarioExistingTestCasePayload(
                String.valueOf(testCase.getId()),
                endpointId,
                testCase.getName(),
                testCase.getType().name(),
                testCase.getDescription()
        );
    }

    private ScenarioRecommendationResponse toRecommendation(
            ScenarioPayload scenario,
            ScenarioType fallbackType,
            Map<String, Long> apiIdByEndpointId
    ) {
        String reason = scenario.meta() == null || scenario.meta().rationale() == null
                ? scenario.description()
                : scenario.meta().rationale();
        return new ScenarioRecommendationResponse(
                scenario.name(),
                fallbackType == null ? ScenarioType.HAPPY_PATH : fallbackType,
                reason,
                scenario.steps() == null ? List.of() : scenario.steps().stream()
                        .map(step -> toRecommendationStep(step, apiIdByEndpointId))
                        .toList()
        );
    }

    private ScenarioRecommendationResponse.Step toRecommendationStep(
            ScenarioStepPayload step,
            Map<String, Long> apiIdByEndpointId
    ) {
        return new ScenarioRecommendationResponse.Step(
                step.order(),
                apiIdByEndpointId.get(step.endpoint_id()),
                step.endpoint_id(),
                step.name() == null || step.name().isBlank() ? step.description() : step.name(),
                requestConfig(step),
                jsonString(step.chained_variables()),
                validationRules(step)
        );
    }

    private Map<String, Long> scenarioApiIdByEndpointId(List<ApiInventory> inventories, List<ApiEndpoint> endpoints) {
        Map<String, Long> apiIds = new LinkedHashMap<>();
        inventories.forEach(inventory -> apiIds.put(
                endpointId(inventory.getMethod().name(), inventory.getEndpointPath()),
                inventory.getId()
        ));
        endpoints.forEach(endpoint -> apiIds.put(
                endpointId(endpoint.getMethod().name(), endpoint.getPath()),
                endpoint.getId()
        ));
        return apiIds;
    }

    private List<String> scenarioSummaries(List<ScenarioPayload> scenarios) {
        return scenarios.stream()
                .map(scenario -> "%s(stepCount=%d, endpointIds=%s)".formatted(
                        scenario.name(),
                        scenario.steps() == null ? 0 : scenario.steps().size(),
                        scenario.steps() == null ? List.of() : scenario.steps().stream()
                                .map(ScenarioStepPayload::endpoint_id)
                                .toList()
                ))
                .toList();
    }

    private String requestConfig(ScenarioStepPayload step) {
        ObjectNode requestConfig = objectMapper.createObjectNode();
        if (step.static_payload() != null && !step.static_payload().isNull()) {
            requestConfig.set("body", step.static_payload());
        }
        if (step.static_params() != null && !step.static_params().isNull()) {
            requestConfig.set("params", step.static_params());
        }
        return requestConfig.isEmpty() ? null : jsonString(requestConfig);
    }

    private String validationRules(ScenarioStepPayload step) {
        ObjectNode validationRules = objectMapper.createObjectNode();
        if (step.expected_status_code() != null) {
            validationRules.put("expectedStatusCode", step.expected_status_code());
        }
        if (step.expected_assertions() != null && !step.expected_assertions().isEmpty()) {
            validationRules.set("assertions", objectMapper.valueToTree(step.expected_assertions()));
        }
        return validationRules.isEmpty() ? null : jsonString(validationRules);
    }

    private String jsonString(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return value.toString();
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    @Transactional(readOnly = true)
    public Scenario getScenario(Long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Scenario not found."));
    }

    private List<ScenarioStepResponse> replaceSteps(Scenario scenario, List<ScenarioStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ScenarioStepResponse> responses = new ArrayList<>();
        for (ScenarioStepRequest step : steps) {
            ApiInventory apiInventory = apiInventoryRepository.findById(step.apiId()).orElse(null);
            ApiEndpoint apiEndpoint = apiInventory == null
                    ? apiEndpointService.getApiEndpoint(step.apiId())
                    : endpointForInventory(scenario.getApp(), apiInventory);
            log.info("Saving scenario step. scenarioId={}, stepOrder={}, requestApiId={}, resolvedEndpointId={}, resolvedInventoryId={}, label={}",
                    scenario.getId(),
                    step.stepOrder(),
                    step.apiId(),
                    apiEndpoint.getId(),
                    apiInventory == null ? null : apiInventory.getId(),
                    step.label());
            ScenarioStep saved = scenarioStepRepository.save(ScenarioStep.builder()
                    .scenario(scenario)
                    .stepOrder(step.stepOrder())
                    .apiEndpoint(apiEndpoint)
                    .apiInventory(apiInventory)
                    .label(step.label())
                    .requestConfig(step.requestConfig())
                    .extractRules(step.extractRules())
                    .validationRules(step.validationRules())
                    .build());
            responses.add(ScenarioStepResponse.from(saved));
        }
        return responses.stream()
                .sorted(java.util.Comparator.comparing(ScenarioStepResponse::stepOrder))
                .toList();
    }

    private List<ScenarioStepResponse> getStepResponses(Long scenarioId) {
        return scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId)
                .stream()
                .map(ScenarioStepResponse::from)
                .toList();
    }

    private ApiEndpoint endpointForInventory(App app, ApiInventory apiInventory) {
        ApiMethod method = ApiMethod.valueOf(apiInventory.getMethod().name());
        return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(app.getId(), method, apiInventory.getEndpointPath())
                .orElseGet(() -> {
                    ApiEndpoint endpoint = apiEndpointRepository.save(ApiEndpoint.builder()
                            .app(app)
                            .method(method)
                            .path(apiInventory.getEndpointPath())
                            .domainTag(apiInventory.getDomainTag())
                            .controllerName(apiInventory.getOperationId())
                            .requestSchema(apiInventory.getRequestSchema())
                            .responseSchema(apiInventory.getResponseSchema())
                            .deprecated(false)
                            .build());
                    log.info("Created legacy API endpoint from inventory for scenario step. appId={}, inventoryId={}, endpointId={}, method={}, path={}",
                            app.getId(),
                            apiInventory.getId(),
                            endpoint.getId(),
                            method,
                            apiInventory.getEndpointPath());
                    return endpoint;
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 2000 ? compacted.substring(0, 2000) + "..." : compacted;
    }
}

