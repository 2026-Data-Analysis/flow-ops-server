package flowops.scenario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flowops.execution.support.ResponseMetadataSupport;
import flowops.execution.support.ResponseMetadataSupport.ResponseMetadata;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioStepPayload;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
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
import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final EnvironmentRepository environmentRepository;
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
        Environment environment = resolveCreateEnvironment(request, app);
        try {
            Scenario scenario = scenarioRepository.save(Scenario.builder()
                    .app(app)
                    .environment(environment)
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
    public List<ScenarioSummaryResponse> listByApp(Long appId, Long environmentId, Long repositoryId, String branchName, String domainTag, String method) {
        appService.getApp(appId);
        List<Scenario> scenarios = scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId);
        return scenarios.stream()
                .filter(scenario -> matchesStepFilter(scenario, domainTag, method))
                .map(scenario -> ScenarioSummaryResponse.from(
                        scenario,
                        scenarioStepRepository.countByScenarioId(scenario.getId())
                ))
                .toList();
    }

    private boolean matchesStepFilter(Scenario scenario, String domainTag, String method) {
        if ((domainTag == null || domainTag.isBlank()) && (method == null || method.isBlank())) {
            return true;
        }
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenario.getId());
        return steps.stream().anyMatch(step -> {
            ApiEndpoint endpoint = step.getApiEndpoint();
            if (endpoint == null) return false;
            boolean domainMatch = domainTag == null || domainTag.isBlank() || domainTag.equals(endpoint.getDomainTag());
            boolean methodMatch = method == null || method.isBlank() || method.equals(endpoint.getMethod().name());
            return domainMatch && methodMatch;
        });
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
            executionStepLogRepository.clearScenarioStepReferencesByScenarioId(scenarioId);
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

        ScenarioGenerateResponse response = aiClient.buildScenario(buildScenarioGenerateRequest(
                request,
                app,
                projectId,
                aiEndpoints
        ));

        List<ScenarioPayload> scenarios = scenarios(response);
        if (response == null || scenarios.isEmpty()) {
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
                response.data() == null ? List.of() : response.data().used_endpoint_ids(),
                scenarioSummaries(scenarios));

        List<ScenarioRecommendationResponse> recommendations = scenarios.stream()
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

    @Transactional(readOnly = true)
    public ScenarioGenerateResponse generateV2(RecommendScenarioRequest request) {
        if (request == null || request.appId() == null) {
            return new ScenarioGenerateResponse(null, null, false, null, "INVALID_REQUEST", "appId is required", null);
        }
        App app = appService.getApp(request.appId());
        List<ApiInventory> inventories = scenarioInventories(app.getId(), request.apiIds());
        List<ApiEndpoint> endpoints = inventories.isEmpty() ? scenarioEndpoints(app.getId(), request.apiIds()) : List.of();
        List<ScenarioEndpointPayload> aiEndpoints = !inventories.isEmpty()
                ? inventories.stream().map(this::toScenarioEndpointPayload).toList()
                : endpoints.stream().map(this::toScenarioEndpointPayload).toList();
        String projectId = projectId(app, inventories);

        return aiClient.buildScenario(buildScenarioGenerateRequest(
                request,
                app,
                projectId,
                aiEndpoints
        ));
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
        // 요청된 apiId를 인벤토리로 해석한다. (프론트가 이미 프로젝트/리포지토리/브랜치로 스코프한 ID)
        List<ApiInventory> resolved = apiIds.stream()
                .map(apiInventoryRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
        // 우선 요청 appId에 속한 인벤토리를 사용한다.
        List<ApiInventory> appScoped = resolved.stream()
                .filter(inventory -> inventory.getRepositoryInfo() != null
                        && inventory.getRepositoryInfo().getApp() != null
                        && inventory.getRepositoryInfo().getApp().getId().equals(appId))
                .toList();
        log.info("Resolved scenario API inventory by requested ids. appId={}, requestedApiIdCount={}, resolvedCount={}, appScopedCount={}, firstRequestedApiIds={}, firstResolvedInventoryIds={}",
                appId,
                apiIds.size(),
                resolved.size(),
                appScoped.size(),
                apiIds.stream().limit(10).toList(),
                resolved.stream().limit(10).map(ApiInventory::getId).toList());
        // appId 스코프 결과가 비어 있으면(프론트의 appId가 인벤토리 소유 app과 어긋난 경우)
        // 요청된 ID로 해석된 인벤토리를 그대로 사용한다. 그렇지 않으면 app 스코프 결과를 사용.
        if (appScoped.isEmpty() && !resolved.isEmpty()) {
            log.warn("Requested apiIds belong to a different app than request.appId. Falling back to resolved inventories. requestAppId={}, inventoryAppIds={}",
                    appId,
                    resolved.stream().limit(10)
                            .map(inv -> inv.getRepositoryInfo() != null && inv.getRepositoryInfo().getApp() != null
                                    ? inv.getRepositoryInfo().getApp().getId() : null)
                            .toList());
            return resolved;
        }
        return appScoped;
    }

    private List<ApiEndpoint> scenarioEndpoints(Long appId, List<Long> apiIds) {
        if (apiIds == null || apiIds.isEmpty()) {
            List<ApiEndpoint> endpoints = apiEndpointRepository.findByAppId(appId);
            log.info("Resolved scenario API endpoints by app fallback. appId={}, endpointCount={}", appId, endpoints.size());
            return endpoints;
        }
        // 누락된 id가 있어도 throw하지 않고 조회된 엔드포인트만 사용한다.
        List<ApiEndpoint> endpoints = apiIds.stream()
                .map(apiEndpointRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(endpoint -> endpoint.getApp() != null && endpoint.getApp().getId().equals(appId))
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

    private ScenarioGenerateRequest buildScenarioGenerateRequest(
            RecommendScenarioRequest request,
            App app,
            String projectId,
            List<ScenarioEndpointPayload> apis
    ) {
        String mode = scenarioMode(request);
        return new ScenarioGenerateRequest(
                projectId,
                mode,
                "NATURAL_LANGUAGE".equals(mode) ? userIntent(request) : null,
                new ScenarioApiInventoryPayload(projectId, apis),
                environmentPayload(request, resolveEnvironment(request, app)),
                "RECOMMEND".equals(mode) ? existingTestCases(app.getId()) : null,
                existingScenarios(app.getId()),
                maxScenarios(request, mode),
                maxStepsPerScenario(request, mode)
        );
    }

    private Integer maxScenarios(RecommendScenarioRequest request, String mode) {
        if (request.maxScenarios() != null) {
            return request.maxScenarios();
        }
        return "NATURAL_LANGUAGE".equals(mode) ? 2 : 3;
    }

    private Integer maxStepsPerScenario(RecommendScenarioRequest request, String mode) {
        if (request.maxStepsPerScenario() != null) {
            return request.maxStepsPerScenario();
        }
        return "NATURAL_LANGUAGE".equals(mode) ? 5 : null;
    }

    private EnvironmentPayload environmentPayload(RecommendScenarioRequest request, Environment environment) {
        if (environment == null) {
            return request.environmentId() == null
                    ? null
                    : new EnvironmentPayload(
                            String.valueOf(request.environmentId()),
                            null,
                            null,
                            null,
                            null,
                            objectMapper.nullNode(),
                            objectMapper.nullNode()
                    );
        }
        return new EnvironmentPayload(
                String.valueOf(environment.getId()),
                environment.getName(),
                environment.getBaseUrl(),
                environment.getDefaultTestLevel() == null ? null : environment.getDefaultTestLevel().name(),
                environment.getAuthType() == null ? null : environment.getAuthType().name(),
                meaningfulJsonOrNull(parseJson(environment.getAuthConfig())),
                meaningfulJsonOrNull(parseJson(environment.getHeaders()))
        );
    }

    private String testLevel(RecommendScenarioRequest request, Environment environment) {
        if (request.testLevel() != null) {
            return request.testLevel().name();
        }
        return environment == null || environment.getDefaultTestLevel() == null
                ? null
                : environment.getDefaultTestLevel().name();
    }

    private ScenarioEndpointPayload toScenarioEndpointPayload(ApiInventory inventory) {
        JsonNode requestSchema = parseJson(inventory.getRequestSchema());
        JsonNode responseSchema = parseJson(inventory.getResponseSchema());
        ResponseMetadata metadata = ResponseMetadataSupport.from(inventory.getResponseSchema(), null);
        return new ScenarioEndpointPayload(
                endpointId(inventory.getMethod().name(), inventory.getEndpointPath()),
                inventory.getEndpointPath(),
                inventory.getMethod().name(),
                inventory.getSummary(),
                inventory.getOperationId(),
                parameters(requestSchema),
                authPayload(inventory.isAuthRequired()),
                requestBodySchema(requestSchema),
                responseSchema,
                metadata.expectedStatusCodes(),
                metadata.errorStatusCodes(),
                metadata.errorCodes(),
                tags(inventory.getDomainTag())
        );
    }

    private ScenarioEndpointPayload toScenarioEndpointPayload(ApiEndpoint endpoint) {
        JsonNode requestSchema = parseJson(endpoint.getRequestSchema());
        JsonNode responseSchema = parseJson(endpoint.getResponseSchema());
        ResponseMetadata metadata = ResponseMetadataSupport.from(endpoint.getResponseSchema(), null);
        return new ScenarioEndpointPayload(
                endpointId(endpoint.getMethod().name(), endpoint.getPath()),
                endpoint.getPath(),
                endpoint.getMethod().name(),
                endpoint.getDomainTag(),
                endpoint.getControllerName(),
                parameters(requestSchema),
                null,
                requestBodySchema(requestSchema),
                responseSchema,
                metadata.expectedStatusCodes(),
                metadata.errorStatusCodes(),
                metadata.errorCodes(),
                tags(endpoint.getDomainTag())
        );
    }

    private Environment resolveEnvironment(RecommendScenarioRequest request, App app) {
        if (request.environmentId() != null) {
            return environmentRepository.findById(request.environmentId())
                    .filter(environment -> environment.getApp() != null && environment.getApp().getId().equals(app.getId()))
                    .orElse(null);
        }
        if (app.getDefaultBranch() != null && !app.getDefaultBranch().isBlank()) {
            return environmentRepository
                    .findFirstByAppIdAndBranchNameOrderByCreatedAtAsc(app.getId(), app.getDefaultBranch())
                    .orElseGet(() -> environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(app.getId()).orElse(null));
        }
        return environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(app.getId()).orElse(null);
    }

    private ScenarioAuthPayload authPayload(boolean authRequired) {
        return authRequired
                ? new ScenarioAuthPayload("bearer", "header")
                : new ScenarioAuthPayload("none", null);
    }

    private List<String> tags(String domainTag) {
        return domainTag == null || domainTag.isBlank() ? List.of() : List.of(domainTag);
    }

    private JsonNode requestBodySchema(JsonNode requestSchema) {
        if (requestSchema == null || requestSchema.isNull() || requestSchema.isMissingNode()) {
            return objectMapper.nullNode();
        }
        if (requestSchema.isObject() && requestSchema.has("body")) {
            return requestSchema.get("body");
        }
        return requestSchema;
    }

    private JsonNode parameters(JsonNode requestSchema) {
        ArrayNode parameters = objectMapper.createArrayNode();
        addParameters(parameters, requestSchema, "pathParams", "path");
        addParameters(parameters, requestSchema, "queryParams", "query");
        addParameters(parameters, requestSchema, "headers", "header");
        // AI 서버는 parameters가 null이 아닌 list여야 하므로 비어 있어도 빈 배열을 반환한다.
        return parameters;
    }

    private void addParameters(ArrayNode target, JsonNode requestSchema, String sourceField, String location) {
        if (requestSchema == null || !requestSchema.has(sourceField) || !requestSchema.get(sourceField).isObject()) {
            return;
        }
        requestSchema.get(sourceField).fields().forEachRemaining(entry -> {
            ObjectNode parameter = objectMapper.createObjectNode();
            parameter.put("name", entry.getKey());
            parameter.put("in", location);
            parameter.set("schema", entry.getValue());
            target.add(parameter);
        });
    }

    private String endpointId(String method, String path) {
        return method + ":" + path;
    }

    private List<ScenarioExistingTestCasePayload> existingTestCases(Long appId) {
        return testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(appId).stream()
                .map(this::toScenarioExistingTestCasePayload)
                .toList();
    }

    private List<ExistingScenarioSummary> existingScenarios(Long appId) {
        return scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId).stream()
                .map(scenario -> {
                    // step_api_ids는 api_inventory[].endpoint_id(method:path)와 동일한 문자열이어야
                    // AI 서버에서 dedup 매칭이 된다. 숫자 DB id가 아니라 endpoint_id 문자열로 통일한다.
                    List<String> stepApiIds = scenarioStepRepository
                            .findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                            .stream()
                            .map(step -> {
                                if (step.getApiInventory() != null) {
                                    return endpointId(step.getApiInventory().getMethod().name(),
                                            step.getApiInventory().getEndpointPath());
                                }
                                if (step.getApiEndpoint() != null) {
                                    return endpointId(step.getApiEndpoint().getMethod().name(),
                                            step.getApiEndpoint().getPath());
                                }
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    return new ExistingScenarioSummary(scenario.getName(), stepApiIds);
                })
                .toList();
    }

    private Environment resolveCreateEnvironment(CreateScenarioRequest request, App app) {
        if (request.environmentId() == null) {
            return null;
        }
        return environmentRepository.findById(request.environmentId())
                .filter(environment -> environment.getApp() != null && environment.getApp().getId().equals(app.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Environment not found for this app."));
    }

    private ScenarioExistingTestCasePayload toScenarioExistingTestCasePayload(TestCase testCase) {
        String endpointId = testCase.getApiInventory() == null
                ? endpointId(testCase.getApiEndpoint().getMethod().name(), testCase.getApiEndpoint().getPath())
                : endpointId(testCase.getApiInventory().getMethod().name(), testCase.getApiInventory().getEndpointPath());
        String testLevel = testCase.getTestLevel() == null ? null : testCase.getTestLevel().name();
        return new ScenarioExistingTestCasePayload(
                String.valueOf(testCase.getId()),
                endpointId,
                testCase.getName(),
                testCase.getType() == null ? null : testCase.getType().name(),
                testCase.getDescription(),
                testLevel,
                testLevel,
                parseJson(testCase.getRequestSpec()),
                parseJson(testCase.getExpectedSpec()),
                parseJson(testCase.getAssertionSpec()),
                extractExpectedStatus(testCase.getExpectedSpec())
        );
    }

    private Integer extractExpectedStatus(String expectedSpec) {
        if (expectedSpec == null || expectedSpec.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(expectedSpec);
            JsonNode statusCode = node.path("statusCode");
            if (!statusCode.isMissingNode() && statusCode.isInt()) {
                return statusCode.intValue();
            }
            JsonNode status = node.path("status");
            if (!status.isMissingNode() && status.isInt()) {
                return status.intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ScenarioRecommendationResponse toRecommendation(
            ScenarioPayload scenario,
            ScenarioType fallbackType,
            Map<String, Long> apiIdByEndpointId
    ) {
        String reason = scenario.meta() == null || scenario.meta().rationale() == null
                ? scenario.description()
                : scenario.meta().rationale();
        // 시나리오 단위 test_level은 Scenario.test_level 우선, 없으면 meta.test_level을 사용한다(스펙상 동일 값).
        String scenarioTestLevel = scenario.test_level() != null
                ? scenario.test_level()
                : scenario.meta() == null ? null : scenario.meta().test_level();
        return new ScenarioRecommendationResponse(
                scenario.name(),
                fallbackType == null ? ScenarioType.HAPPY_PATH : fallbackType,
                reason,
                scenario.steps() == null ? List.of() : scenario.steps().stream()
                        .map(step -> toRecommendationStep(step, apiIdByEndpointId, scenarioTestLevel))
                        .toList()
        );
    }

    private ScenarioRecommendationResponse.Step toRecommendationStep(
            ScenarioStepPayload step,
            Map<String, Long> apiIdByEndpointId,
            String scenarioTestLevel
    ) {
        return new ScenarioRecommendationResponse.Step(
                step.order(),
                apiIdByEndpointId.get(step.apiId()),
                step.apiId(),
                stepLabel(step),
                step.type(),
                step.description(),
                scenarioTestLevel,
                step.userRole(),
                step.stateCondition(),
                step.dataVariant(),
                requestConfig(step),
                jsonString(step.chained_variables()),
                validationRules(step)
        );
    }

    private String stepLabel(ScenarioStepPayload step) {
        if (step.title() != null && !step.title().isBlank()) {
            return step.title();
        }
        return step.description();
    }

    private Map<String, Long> scenarioApiIdByEndpointId(List<ApiInventory> inventories, List<ApiEndpoint> endpoints) {
        Map<String, Long> apiIds = new LinkedHashMap<>();
        inventories.forEach(inventory -> {
            apiIds.put(endpointId(inventory.getMethod().name(), inventory.getEndpointPath()), inventory.getId());
            apiIds.put(String.valueOf(inventory.getId()), inventory.getId());
        });
        endpoints.forEach(endpoint -> {
            apiIds.put(endpointId(endpoint.getMethod().name(), endpoint.getPath()), endpoint.getId());
            apiIds.put(String.valueOf(endpoint.getId()), endpoint.getId());
        });
        return apiIds;
    }

    private List<String> scenarioSummaries(List<ScenarioPayload> scenarios) {
        return scenarios.stream()
                .map(scenario -> "%s(stepCount=%d, endpointIds=%s)".formatted(
                        scenario.name(),
                        scenario.steps() == null ? 0 : scenario.steps().size(),
                        scenario.steps() == null ? List.of() : scenario.steps().stream()
                                .map(ScenarioStepPayload::apiId)
                                .toList()
                ))
                .toList();
    }

    private List<ScenarioPayload> scenarios(ScenarioGenerateResponse response) {
        if (response == null) {
            return List.of();
        }
        if (response.data() != null && response.data().scenarios() != null) {
            return response.data().scenarios();
        }
        return List.of();
    }

    private String requestConfig(ScenarioStepPayload step) {
        ObjectNode requestConfig = step.requestSpec() != null && step.requestSpec().isObject()
                ? step.requestSpec().deepCopy()
                : objectMapper.createObjectNode();
        if (step.execution_endpoint() != null && !step.execution_endpoint().isBlank()
                && !requestConfig.has("endpoint") && !requestConfig.has("path")) {
            requestConfig.put("endpoint", step.execution_endpoint().trim());
        }
        if (step.execution_method() != null && !step.execution_method().isBlank()
                && !requestConfig.has("method")) {
            requestConfig.put("method", step.execution_method().trim().toUpperCase());
        }
        if (step.requestSpec() != null
                && !step.requestSpec().isNull()
                && !step.requestSpec().isMissingNode()
                && !step.requestSpec().isObject()
                && !requestConfig.has("body")) {
            requestConfig.set("body", step.requestSpec());
        }
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
        if (step.expectedSpec() != null && !step.expectedSpec().isNull()) {
            validationRules.set("expectedSpec", step.expectedSpec());
        }
        if (step.assertionSpec() != null && !step.assertionSpec().isNull()) {
            validationRules.set("assertionSpec", step.assertionSpec());
        }
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

    private JsonNode meaningfulJsonOrNull(JsonNode value) {
        return value != null
                && !value.isNull()
                && !value.isMissingNode()
                && !(value.isObject() && value.isEmpty())
                && !(value.isArray() && value.isEmpty())
                && !(value.isTextual() && value.asText().isBlank())
                ? value
                : objectMapper.nullNode();
    }

    @Transactional
    public void delete(Long scenarioId) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "시나리오를 찾을 수 없습니다."));
        executionStepLogRepository.clearScenarioStepReferencesByScenarioId(scenarioId);
        scenarioStepRepository.deleteAll(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId));
        scenarioRepository.delete(scenario);
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
                    .stepId(step.stepId())
                    .ref(step.ref())
                    .chainedVariables(jsonString(step.chainedVariables()))
                    .type(step.type())
                    .testLevel(step.testLevel())
                    .userRole(step.userRole())
                    .stateCondition(step.stateCondition())
                    .dataVariant(step.dataVariant())
                    .requestSpec(jsonString(step.requestSpec()))
                    .expectedSpec(jsonString(step.expectedSpec()))
                    .assertionSpec(jsonString(step.assertionSpec()))
                    .duplicate(step.duplicate())
                    .requestConfig(defaultIfBlank(step.requestConfig(), jsonString(step.requestSpec())))
                    .extractRules(step.extractRules())
                    .validationRules(defaultIfBlank(step.validationRules(), validationRules(step.expectedSpec(), step.assertionSpec())))
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

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String validationRules(JsonNode expectedSpec, JsonNode assertionSpec) {
        ObjectNode validationRules = objectMapper.createObjectNode();
        if (expectedSpec != null && !expectedSpec.isNull()) {
            validationRules.set("expectedSpec", expectedSpec);
            Integer expectedStatus = extractExpectedStatus(jsonString(expectedSpec));
            if (expectedStatus != null) {
                validationRules.put("expectedStatusCode", expectedStatus);
            }
        }
        if (assertionSpec != null && !assertionSpec.isNull()) {
            validationRules.set("assertionSpec", assertionSpec);
        }
        return validationRules.isEmpty() ? null : jsonString(validationRules);
    }
}

