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
import flowops.apiinventory.service.ApiInventoryResolveRequest;
import flowops.apiinventory.service.ApiInventoryResolver;
import flowops.apiinventory.service.ResolvedApiEndpoint;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.config.ScenarioProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.MetaPayload;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateDataPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioStepPayload;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.RecommendScenarioRequest;
import flowops.scenario.dto.request.ReorderScenarioStepsRequest;
import flowops.scenario.dto.request.ScenarioDraftSaveRequest;
import flowops.scenario.dto.request.ScenarioStepDraftRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.request.UpdateScenarioRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioDraftBulkSaveResponse;
import flowops.scenario.dto.response.ScenarioDraftSaveResponse;
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

    private static final String FALLBACK_SCENARIO_PROMPT = """
            프로젝트 생성부터 메이트 좋아요, 매칭까지 이어지는 핵심 사용자 흐름을 E2E 시나리오로 생성해줘.

            반드시 다음 흐름을 중심으로 시나리오를 만들어줘.

            1. 사용자가 프로젝트를 생성한다.
            2. 생성된 프로젝트에서 메이트 후보를 조회하거나 선택한다.
            3. 특정 메이트에게 좋아요를 보낸다.
            4. 상대방도 좋아요를 누르거나 매칭 조건이 충족되어 매칭이 생성된다.
            5. 생성된 매칭 정보를 조회한다.

            API 인벤토리에서 위 흐름에 가장 가까운 endpoint를 찾아 사용해줘.
            정확히 일치하는 endpoint가 없으면 이름, path, method, summary를 기준으로 가장 유사한 API를 선택해줘.
            기존 테스트 케이스와 기존 시나리오에서 커버되지 않은 endpoint를 우선 사용해줘.

            정상 흐름 1개, 예외 흐름 1개, 재조회/검증 흐름 1개를 포함해 최대 3개의 시나리오를 생성해줘.
            각 시나리오는 2~8개의 step으로 구성해줘.
            """;

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final AppService appService;
    private final ApiEndpointService apiEndpointService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiInventoryRepository apiInventoryRepository;
    private final AiClient aiClient;
    private final ExternalServiceProperties externalServiceProperties;
    private final ScenarioProperties scenarioProperties;
    private final ObjectMapper objectMapper;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final EnvironmentRepository environmentRepository;
    private final TestCaseRepository testCaseRepository;
    private final ApiInventoryResolver apiInventoryResolver;

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
        validateScenarioEndpoints(app.getId(), aiEndpoints);
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

        ScenarioGenerateRequest aiRequest = buildScenarioGenerateRequest(
                request,
                app,
                projectId,
                aiEndpoints
        );
        logScenarioGenerateRequest(aiRequest);
        ScenarioGenerateResponse response = buildScenarioWithDemoFallback(aiRequest, app.getId());
        if (isNoScenariosGenerated(response)) {
            logScenarioFailureDiagnostic(aiRequest, response);
            return List.of();
        }
        validateScenarioGenerateResponse(response, app.getId(), aiRequest);

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
        validateScenarioEndpoints(app.getId(), aiEndpoints);
        String projectId = projectId(app, inventories);

        ScenarioGenerateRequest aiRequest = buildScenarioGenerateRequest(
                request,
                app,
                projectId,
                aiEndpoints
        );
        logScenarioGenerateRequest(aiRequest);
        ScenarioGenerateResponse response = buildScenarioWithDemoFallback(aiRequest, app.getId());
        if (isNoScenariosGenerated(response)) {
            logScenarioFailureDiagnostic(aiRequest, response);
            return response;
        }
        validateScenarioGenerateResponse(response, app.getId(), aiRequest);
        return response;
    }

    @Transactional(readOnly = true)
    public ScenarioGenerateResponse buildScenarioWithDemoFallback(ScenarioGenerateRequest request, Long appId) {
        ScenarioGenerateResponse original = null;
        String originalReason = null;
        try {
            original = aiClient.buildScenario(request);
            originalReason = fallbackReason(original, request);
            if (originalReason == null) {
                return original;
            }
        } catch (RuntimeException exception) {
            originalReason = exception.getClass().getSimpleName();
            if (!demoFallbackEnabled()) {
                throw exception;
            }
            original = new ScenarioGenerateResponse(
                    null,
                    null,
                    false,
                    null,
                    originalReason,
                    exception.getMessage(),
                    null
            );
        }

        if (!demoFallbackEnabled()) {
            return original;
        }

        log.warn("Scenario generation failed. Trying demo fallback. projectId={}, appId={}, reason={}, traceId={}",
                request == null ? null : request.project_id(),
                appId,
                originalReason,
                original == null ? null : original.trace_id());

        ScenarioGenerateRequest fallbackRequest = fallbackRequest(request, false);
        ScenarioGenerateResponse fallback = callFallback(request, fallbackRequest, originalReason, false);
        if (fallbackReason(fallback, fallbackRequest) == null) {
            return markFallback(fallback, originalReason);
        }

        if (shouldRetryWithoutExistingScenarios(fallback) && scenarioProperties.demoFallbackMaxRetries() >= 2) {
            log.warn("Scenario fallback retry without existing_scenarios. projectId={}, appId={}",
                    request == null ? null : request.project_id(),
                    appId);
            ScenarioGenerateResponse retry = callFallback(request, fallbackRequest(request, true), originalReason, true);
            if (fallbackReason(retry, fallbackRequest(request, true)) == null) {
                return markFallback(retry, originalReason);
            }
            fallback = retry;
        }

        ScenarioGenerateResponse mock = mockFallback(request, originalReason);
        if (mock != null) {
            return mock;
        }
        return fallback == null ? original : fallback;
    }

    @Transactional
    public ScenarioDraftSaveResponse saveDraft(Long appId, ScenarioDraftSaveRequest request) {
        ScenarioDetailResponse detail = saveDraftDetail(appId, request);
        return new ScenarioDraftSaveResponse(
                detail.id(),
                detail.name(),
                detail.steps() == null ? 0 : detail.steps().size(),
                true
        );
    }

    @Transactional
    public ScenarioDraftBulkSaveResponse saveDrafts(Long appId, List<ScenarioDraftSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "At least one scenario draft is required.");
        }
        List<ScenarioDraftSaveResponse> saved = requests.stream()
                .map(request -> saveDraft(appId, request))
                .toList();
        log.info("Bulk saved orchestrator generated scenarios. savedCount={}", saved.size());
        return new ScenarioDraftBulkSaveResponse(saved.size(), saved);
    }

    private ScenarioDetailResponse saveDraftDetail(Long pathAppId, ScenarioDraftSaveRequest request) {
        if (request == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario draft is required.");
        }
        Long appId = pathAppId != null ? pathAppId : request.appId();
        if (appId == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "appId is required.");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario draft name is required.");
        }
        App app = appService.getApp(appId);
        List<ScenarioStepDraftRequest> draftSteps = assignDraftStepOrders(request.steps());
        log.info("Saving orchestrator generated scenario. projectId={}, appId={}, scenarioName={}, stepCount={}",
                request.projectId(),
                app.getId(),
                request.name(),
                draftSteps.size());

        Scenario scenario = scenarioRepository.save(Scenario.builder()
                .app(app)
                .environment(null)
                .name(request.name().trim())
                .description(request.description())
                .type(parseScenarioType(request.type()))
                .recommendationReason(recommendationReason(request))
                .source(ScenarioSource.AI)
                .build());

        List<ApiInventory> inventories = inventoriesForDraft(app, request.projectId());
        for (ScenarioStepDraftRequest step : draftSteps) {
            saveDraftStep(scenario, request.projectId(), request.testLevel(), step, inventories);
        }
        List<ScenarioStepResponse> steps = getStepResponses(scenario.getId());
        log.info("Saved orchestrator generated scenario. scenarioId={}, stepCount={}",
                scenario.getId(),
                steps.size());
        return ScenarioDetailResponse.of(scenario, steps);
    }

    private void validateScenarioEndpoints(Long appId, List<ScenarioEndpointPayload> aiEndpoints) {
        if (aiEndpoints == null || aiEndpoints.isEmpty()) {
            log.warn("Scenario generation blocked because api_inventory.endpoints is empty. appId={}", appId);
            throw new ApiException(ErrorCode.INVALID_INPUT, "api_inventory.endpoints must contain at least one endpoint.");
        }
    }

    private void validateScenarioGenerateResponse(ScenarioGenerateResponse response, Long appId, ScenarioGenerateRequest request) {
        if (response != null && Boolean.FALSE.equals(response.success())) {
            log.warn("AI scenario generator returned failure. appId={}, errorCode={}, errorMessage={}, traceId={}",
                    appId,
                    response.error_code(),
                    response.error_message(),
                    response.trace_id());
            logScenarioFailureDiagnostic(request, response);
            throw new ApiException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "AI scenario generator failed. error_code=%s, error_message=%s, trace_id=%s"
                            .formatted(response.error_code(), response.error_message(), response.trace_id())
            );
        }
    }

    private boolean isNoScenariosGenerated(ScenarioGenerateResponse response) {
        return response != null
                && Boolean.FALSE.equals(response.success())
                && "NO_SCENARIOS_GENERATED".equals(response.error_code());
    }

    private boolean demoFallbackEnabled() {
        return scenarioProperties == null || scenarioProperties.demoFallbackEnabled();
    }

    private String fallbackReason(ScenarioGenerateResponse response, ScenarioGenerateRequest request) {
        if (response == null) {
            return "NO_RESPONSE";
        }
        if (isRecommendMode(request) && (hasText(response.error_code()) || hasText(response.error_message()))) {
            return defaultIfBlank(response.error_code(), "SCENARIO_AGENT_ERROR");
        }
        if (Boolean.FALSE.equals(response.success())) {
            return defaultIfBlank(response.error_code(), "SCENARIO_AGENT_FAILED");
        }
        if (response.data() == null || response.data().scenarios() == null || response.data().scenarios().isEmpty()) {
            return "NO_SCENARIOS_GENERATED";
        }
        return null;
    }

    private boolean isRecommendMode(ScenarioGenerateRequest request) {
        return request != null && "RECOMMEND".equalsIgnoreCase(request.mode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ScenarioGenerateRequest fallbackRequest(ScenarioGenerateRequest request, boolean clearExistingScenarios) {
        return new ScenarioGenerateRequest(
                request.project_id(),
                "NATURAL_LANGUAGE",
                FALLBACK_SCENARIO_PROMPT,
                request.api_inventory(),
                request.environment(),
                request.existing_test_cases(),
                clearExistingScenarios ? List.of() : request.existing_scenarios(),
                request.max_scenarios() == null ? 3 : request.max_scenarios(),
                request.max_steps_per_scenario() == null ? 8 : request.max_steps_per_scenario()
        );
    }

    private ScenarioGenerateResponse callFallback(
            ScenarioGenerateRequest originalRequest,
            ScenarioGenerateRequest fallbackRequest,
            String originalReason,
            boolean withoutExistingScenarios
    ) {
        try {
            int endpointCount = fallbackRequest.api_inventory() == null || fallbackRequest.api_inventory().endpoints() == null
                    ? 0
                    : fallbackRequest.api_inventory().endpoints().size();
            int existingScenarioCount = fallbackRequest.existing_scenarios() == null
                    ? 0
                    : fallbackRequest.existing_scenarios().size();
            log.info("Scenario fallback request prepared. promptType=DEMO_PROJECT_MATE_MATCHING, endpointCount={}, existingScenarioCount={}",
                    endpointCount,
                    existingScenarioCount);
            ScenarioGenerateResponse response = aiClient.buildScenario(fallbackRequest);
            String reason = fallbackReason(response, fallbackRequest);
            if (reason == null) {
                int scenarioCount = response.data() == null || response.data().scenarios() == null ? 0 : response.data().scenarios().size();
                int usedEndpointCount = response.data() == null || response.data().used_endpoint_ids() == null ? 0 : response.data().used_endpoint_ids().size();
                log.info("Scenario fallback succeeded. scenarioCount={}, usedEndpointCount={}, originalTraceId={}, fallbackTraceId={}",
                        scenarioCount,
                        usedEndpointCount,
                        null,
                        response.trace_id());
            } else {
                log.warn("Scenario fallback failed. reason={}, originalTraceId={}, fallbackTraceId={}",
                        reason,
                        null,
                        response == null ? null : response.trace_id());
            }
            return response;
        } catch (RuntimeException exception) {
            String reason = exception.getClass().getSimpleName();
            log.warn("Scenario fallback failed. reason={}, originalTraceId={}, fallbackTraceId={}",
                    reason,
                    null,
                    null);
            return new ScenarioGenerateResponse(
                    null,
                    null,
                    false,
                    null,
                    reason,
                    exception.getMessage(),
                    null
            );
        }
    }

    private boolean shouldRetryWithoutExistingScenarios(ScenarioGenerateResponse response) {
        return response != null && "NO_SCENARIOS_GENERATED".equals(response.error_code());
    }

    private ScenarioGenerateResponse markFallback(ScenarioGenerateResponse response, String reason) {
        ScenarioGenerateDataPayload data = response.data();
        ScenarioGenerateDataPayload markedData = new ScenarioGenerateDataPayload(
                data == null ? List.of() : data.scenarios(),
                data == null ? List.of() : data.used_endpoint_ids(),
                true,
                reason,
                "DEMO_PROJECT_MATE_MATCHING"
        );
        return new ScenarioGenerateResponse(
                response.requestId(),
                response.generationId(),
                true,
                markedData,
                null,
                null,
                response.trace_id()
        );
    }

    private ScenarioGenerateResponse mockFallback(ScenarioGenerateRequest request, String originalReason) {
        List<ScenarioEndpointPayload> endpoints = request == null || request.api_inventory() == null || request.api_inventory().endpoints() == null
                ? List.of()
                : request.api_inventory().endpoints();
        List<ScenarioEndpointPayload> selected = selectMockFallbackEndpoints(endpoints);
        if (selected.size() < 2) {
            return null;
        }
        List<ScenarioStepPayload> steps = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            ScenarioEndpointPayload endpoint = selected.get(index);
            steps.add(new ScenarioStepPayload(
                    "fallback-step-" + (index + 1),
                    "fallback_" + (index + 1),
                    index + 1,
                    objectMapper.createArrayNode(),
                    endpoint.endpoint_id(),
                    endpoint.endpoint_id(),
                    mockStepName(index),
                    mockStepName(index),
                    endpoint.summary(),
                    "HAPPY_PATH",
                    "SANITY",
                    "USER",
                    null,
                    null,
                    endpoint.path(),
                    endpoint.method(),
                    mockRequestSpec(endpoint),
                    mockExpectedSpec(),
                    mockAssertionSpec(),
                    false,
                    objectMapper.createObjectNode(),
                    objectMapper.createObjectNode(),
                    200,
                    List.of("status code is successful")
            ));
        }
        ScenarioPayload scenario = new ScenarioPayload(
                "demo-fallback-" + UUID.randomUUID(),
                "프로젝트 생성-메이트 좋아요-매칭 데모 흐름",
                "기본 요청에서 시나리오 생성에 실패하여, 데모용 추천 흐름으로 대체 생성했습니다.",
                "HAPPY_PATH",
                "SANITY",
                steps,
                new MetaPayload(
                        "기본 요청에서 시나리오 생성에 실패하여, 데모용 추천 흐름으로 대체 생성했습니다.",
                        null,
                        "SANITY",
                        "MEDIUM"
                )
        );
        List<String> selectedEndpointIds = selected.stream()
                .map(ScenarioEndpointPayload::endpoint_id)
                .filter(Objects::nonNull)
                .toList();
        log.info("Scenario mock fallback generated. scenarioCount={}, selectedEndpointIds={}",
                1,
                selectedEndpointIds);
        return new ScenarioGenerateResponse(
                null,
                null,
                true,
                new ScenarioGenerateDataPayload(
                        List.of(scenario),
                        selectedEndpointIds,
                        true,
                        originalReason,
                        "DEMO_PROJECT_MATE_MATCHING"
                ),
                null,
                null,
                null
        );
    }

    private List<ScenarioEndpointPayload> selectMockFallbackEndpoints(List<ScenarioEndpointPayload> endpoints) {
        List<ScenarioEndpointPayload> selected = new ArrayList<>();
        addIfPresent(selected, bestEndpoint(endpoints, List.of("project", "projects", "프로젝트", "생성", "create"), List.of("POST")));
        addIfPresent(selected, bestEndpoint(endpoints, List.of("mate", "mates", "메이트", "like", "likes", "좋아요", "favorite"), List.of("GET", "POST")));
        addIfPresent(selected, bestEndpoint(endpoints, List.of("match", "matching", "matches", "매칭"), List.of("POST", "GET")));
        return selected.stream().distinct().limit(3).toList();
    }

    private ScenarioEndpointPayload bestEndpoint(List<ScenarioEndpointPayload> endpoints, List<String> keywords, List<String> methods) {
        return endpoints.stream()
                .filter(endpoint -> methods.isEmpty() || methods.contains(safeUpper(endpoint.method())))
                .max(Comparator.comparingInt(endpoint -> endpointScore(endpoint, keywords)))
                .filter(endpoint -> endpointScore(endpoint, keywords) > 0)
                .orElse(null);
    }

    private int endpointScore(ScenarioEndpointPayload endpoint, List<String> keywords) {
        String haystack = "%s %s %s %s".formatted(
                endpoint.endpoint_id(),
                endpoint.method(),
                endpoint.path(),
                endpoint.summary()
        ).toLowerCase();
        int score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase())) {
                score++;
            }
        }
        return score;
    }

    private void addIfPresent(List<ScenarioEndpointPayload> endpoints, ScenarioEndpointPayload endpoint) {
        if (endpoint != null && !endpoints.contains(endpoint)) {
            endpoints.add(endpoint);
        }
    }

    private ObjectNode mockRequestSpec(ScenarioEndpointPayload endpoint) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("method", endpoint.method());
        node.put("path", endpoint.path());
        node.set("body", objectMapper.createObjectNode());
        node.set("queryParams", objectMapper.createObjectNode());
        return node;
    }

    private ObjectNode mockExpectedSpec() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("statusCode", 200);
        return node;
    }

    private ObjectNode mockAssertionSpec() {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("bodyContains", objectMapper.valueToTree(List.of("success")));
        return node;
    }

    private String mockStepName(int index) {
        return switch (index) {
            case 0 -> "프로젝트 생성";
            case 1 -> "메이트 좋아요";
            default -> "매칭 조회";
        };
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private void logScenarioGenerateRequest(ScenarioGenerateRequest request) {
        if (!log.isDebugEnabled() || request == null) {
            return;
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project_id", request.project_id());
        fields.put("mode", request.mode());
        fields.put("user_intent", request.user_intent());
        fields.put("api_inventory_project_id", request.api_inventory() == null ? null : request.api_inventory().project_id());
        fields.put("endpoint_count", endpointCount(request));
        fields.put("sample_endpoints", sampleEndpointDiagnostics(request));
        fields.put("existing_test_cases_count", request.existing_test_cases() == null ? 0 : request.existing_test_cases().size());
        fields.put("existing_scenarios_count", request.existing_scenarios() == null ? 0 : request.existing_scenarios().size());
        fields.put("sample_existing_scenarios", sampleExistingScenarioDiagnostics(request));
        log.debug("AI scenario generator request body summary: {}", fields);
    }

    private void logScenarioFailureDiagnostic(ScenarioGenerateRequest request, ScenarioGenerateResponse response) {
        log.warn("recommend failure diagnostic (dedup check): mode={} endpointCount={} existingScenarioCount={} existingTestCaseCount={} sampleEndpointIds={} sampleExistingScenarioStepApiIds={} errorCode={} errorMessage={} traceId={}",
                request == null ? null : request.mode(),
                endpointCount(request),
                request == null || request.existing_scenarios() == null ? 0 : request.existing_scenarios().size(),
                request == null || request.existing_test_cases() == null ? 0 : request.existing_test_cases().size(),
                sampleEndpointIds(request),
                sampleExistingScenarioStepApiIds(request),
                response == null ? null : response.error_code(),
                response == null ? null : response.error_message(),
                response == null ? null : response.trace_id());
    }

    private int endpointCount(ScenarioGenerateRequest request) {
        return request == null || request.api_inventory() == null || request.api_inventory().endpoints() == null
                ? 0
                : request.api_inventory().endpoints().size();
    }

    private List<String> sampleEndpointIds(ScenarioGenerateRequest request) {
        if (request == null || request.api_inventory() == null || request.api_inventory().endpoints() == null) {
            return List.of();
        }
        return request.api_inventory().endpoints().stream()
                .limit(3)
                .map(ScenarioEndpointPayload::endpoint_id)
                .toList();
    }

    private List<List<String>> sampleExistingScenarioStepApiIds(ScenarioGenerateRequest request) {
        if (request == null || request.existing_scenarios() == null) {
            return List.of();
        }
        return request.existing_scenarios().stream()
                .limit(3)
                .map(ExistingScenarioSummary::step_api_ids)
                .toList();
    }

    private List<Map<String, Object>> sampleEndpointDiagnostics(ScenarioGenerateRequest request) {
        if (request == null || request.api_inventory() == null || request.api_inventory().endpoints() == null) {
            return List.of();
        }
        return request.api_inventory().endpoints().stream()
                .limit(3)
                .map(endpoint -> {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("endpoint_id", endpoint.endpoint_id());
                    fields.put("path", endpoint.path());
                    fields.put("method", endpoint.method());
                    fields.put("summary", endpoint.summary());
                    fields.put("has_request_body_schema", hasJsonValue(endpoint.request_body_schema()));
                    fields.put("has_response_schema", hasJsonValue(endpoint.response_schema()));
                    fields.put("auth", endpoint.auth());
                    fields.put("tags", endpoint.tags());
                    return fields;
                })
                .toList();
    }

    private List<Map<String, Object>> sampleExistingScenarioDiagnostics(ScenarioGenerateRequest request) {
        if (request == null || request.existing_scenarios() == null) {
            return List.of();
        }
        return request.existing_scenarios().stream()
                .limit(3)
                .map(scenario -> {
                    Map<String, Object> fields = new LinkedHashMap<>();
                    fields.put("name", scenario.name());
                    fields.put("step_api_ids", scenario.step_api_ids());
                    return fields;
                })
                .toList();
    }

    private boolean hasJsonValue(JsonNode value) {
        return value != null
                && !value.isNull()
                && !value.isMissingNode()
                && !(value.isObject() && value.isEmpty())
                && !(value.isArray() && value.isEmpty());
    }

    private List<ScenarioRecommendationResponse> mockRecommendations() {
        return List.of(
                new ScenarioRecommendationResponse("Critical checkout flow", ScenarioType.HAPPY_PATH, "Covers a high-value multi-endpoint business path.", "REGRESSION", "HIGH", List.of()),
                new ScenarioRecommendationResponse("Validation guard rails", ScenarioType.EDGE_CASE, "Focuses on required-field and malformed-input failures.", "REGRESSION", "MEDIUM", List.of()),
                new ScenarioRecommendationResponse("Recovery after dependency failure", ScenarioType.FAILURE_RECOVERY, "Models retry and fallback behavior after an upstream error.", "REGRESSION", "HIGH", List.of())
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
                existingTestCases(app.getId()),
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
        String estimatedRisk = scenario.meta() == null ? null : scenario.meta().estimated_risk();
        return new ScenarioRecommendationResponse(
                scenario.name(),
                fallbackType == null ? ScenarioType.HAPPY_PATH : fallbackType,
                reason,
                scenarioTestLevel,
                estimatedRisk,
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
                defaultIfBlank(step.test_level(), scenarioTestLevel),
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

    private List<ScenarioStepDraftRequest> assignDraftStepOrders(List<ScenarioStepDraftRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ScenarioStepDraftRequest> normalized = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            ScenarioStepDraftRequest step = steps.get(index);
            int order = step.order() == null ? index + 1 : step.order();
            normalized.add(new ScenarioStepDraftRequest(
                    order,
                    step.apiInventoryId(),
                    step.apiEndpointId(),
                    step.endpointId(),
                    step.apiId(),
                    step.method(),
                    step.path(),
                    step.name(),
                    step.title(),
                    step.description(),
                    step.requestSpec(),
                    step.expectedSpec(),
                    step.assertionSpec(),
                    step.staticPayload(),
                    step.staticParams(),
                    step.expectedStatusCode(),
                    step.expectedAssertions(),
                    step.chainedVariables()
            ));
        }
        return normalized.stream()
                .sorted(Comparator.comparing(ScenarioStepDraftRequest::order))
                .toList();
    }

    private void saveDraftStep(
            Scenario scenario,
            Long projectId,
            String scenarioTestLevel,
            ScenarioStepDraftRequest step,
            List<ApiInventory> inventories
    ) {
        JsonNode requestSpec = normalizedRequestSpec(step);
        EndpointTarget target = endpointTarget(step, requestSpec);
        ResolvedApiEndpoint resolved = apiInventoryResolver.resolve(
                        scenario.getApp(),
                        new ApiInventoryResolveRequest(
                                projectId,
                                scenario.getApp().getId(),
                                step.apiInventoryId(),
                                step.apiEndpointId(),
                                firstNonNull(step.endpointId(), step.apiId()),
                                step.apiId(),
                                target == null ? null : target.method(),
                                target == null ? null : target.path()
                        ),
                        inventories
                )
                .orElseThrow(() -> new ApiException(
                        ErrorCode.INVALID_INPUT,
                        "SCENARIO_STEP_ENDPOINT_NOT_RESOLVED: 시나리오 step의 endpoint를 API inventory와 매칭할 수 없습니다."
                ));

        ScenarioStep saved = scenarioStepRepository.save(ScenarioStep.builder()
                .scenario(scenario)
                .stepOrder(step.order())
                .apiEndpoint(resolved.apiEndpoint())
                .apiInventory(resolved.apiInventory())
                .label(stepName(step))
                .chainedVariables(jsonString(step.chainedVariables()))
                .type(stepType(step, scenario.getType()))
                .testLevel(scenarioTestLevel)
                .requestSpec(jsonString(requestSpec))
                .expectedSpec(jsonString(normalizedExpectedSpec(step)))
                .assertionSpec(jsonString(normalizedAssertionSpec(step)))
                .duplicate(false)
                .requestConfig(jsonString(requestSpec))
                .validationRules(validationRules(normalizedExpectedSpec(step), normalizedAssertionSpec(step)))
                .build());
        log.info("Saved orchestrator generated scenario step. scenarioId={}, stepId={}, order={}, apiInventoryId={}, apiEndpointId={}, endpointId={}",
                scenario.getId(),
                saved.getId(),
                saved.getStepOrder(),
                resolved.apiInventoryId(),
                resolved.apiEndpointId(),
                resolved.endpointId());
    }

    private List<ApiInventory> inventoriesForDraft(App app, Long projectId) {
        if (projectId != null) {
            List<ApiInventory> inventories = apiInventoryRepository.findByProjectIdOrderByIdDesc(projectId);
            if (!inventories.isEmpty()) {
                return inventories;
            }
        }
        return apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(app.getId());
    }

    private ScenarioType parseScenarioType(String value) {
        if (value == null || value.isBlank()) {
            return ScenarioType.HAPPY_PATH;
        }
        try {
            return ScenarioType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return ScenarioType.HAPPY_PATH;
        }
    }

    private String recommendationReason(ScenarioDraftSaveRequest request) {
        if (request.meta() != null && request.meta().isObject()) {
            String rationale = text(request.meta(), "rationale");
            if (rationale != null && !rationale.isBlank()) {
                return rationale;
            }
        }
        return request.description();
    }

    private String stepName(ScenarioStepDraftRequest step) {
        String name = firstNonBlank(step.name(), step.title());
        return name == null || name.isBlank() ? "Step " + step.order() : name;
    }

    private String stepType(ScenarioStepDraftRequest step, ScenarioType scenarioType) {
        String type = firstNonBlank(text(step.requestSpec(), "type"), text(step.assertionSpec(), "type"));
        return type == null || type.isBlank() ? scenarioType.name() : type;
    }

    private JsonNode normalizedRequestSpec(ScenarioStepDraftRequest step) {
        if (isMeaningful(step.requestSpec())) {
            return step.requestSpec();
        }
        ObjectNode request = objectMapper.createObjectNode();
        EndpointTarget target = endpointTarget(step, null);
        if (target != null) {
            request.put("method", target.method());
            request.put("path", target.path());
        }
        if (isMeaningful(step.staticPayload())) {
            request.set("body", step.staticPayload());
        }
        if (isMeaningful(step.staticParams())) {
            request.set("pathParams", step.staticParams());
        }
        if (!request.has("queryParams")) {
            request.set("queryParams", objectMapper.createObjectNode());
        }
        return request;
    }

    private JsonNode normalizedExpectedSpec(ScenarioStepDraftRequest step) {
        if (isMeaningful(step.expectedSpec())) {
            return step.expectedSpec();
        }
        ObjectNode expected = objectMapper.createObjectNode();
        if (step.expectedStatusCode() != null) {
            expected.put("statusCode", step.expectedStatusCode());
        }
        return expected;
    }

    private JsonNode normalizedAssertionSpec(ScenarioStepDraftRequest step) {
        if (isMeaningful(step.assertionSpec())) {
            return step.assertionSpec();
        }
        ObjectNode assertion = objectMapper.createObjectNode();
        if (step.expectedAssertions() != null && !step.expectedAssertions().isEmpty()) {
            assertion.set("bodyContains", objectMapper.valueToTree(step.expectedAssertions()));
        }
        return assertion;
    }

    private EndpointTarget endpointTarget(ScenarioStepDraftRequest step, JsonNode requestSpec) {
        EndpointTarget explicit = endpointTarget(firstNonBlank(step.endpointId(), step.apiId()));
        if (explicit != null) {
            return explicit;
        }
        if (step.method() != null && step.path() != null) {
            return new EndpointTarget(step.method().trim().toUpperCase(), step.path().trim());
        }
        JsonNode node = requestSpec == null ? step.requestSpec() : requestSpec;
        String method = firstNonBlank(text(node, "method"), text(node, "httpMethod"));
        String path = firstNonBlank(text(node, "path"), text(node, "endpoint"));
        if (method == null || path == null) {
            return null;
        }
        return new EndpointTarget(method.trim().toUpperCase(), path.trim());
    }

    private EndpointTarget endpointTarget(String endpointId) {
        if (endpointId == null || endpointId.isBlank()) {
            return null;
        }
        int separator = endpointId.indexOf(':');
        if (separator <= 0 || separator == endpointId.length() - 1) {
            return null;
        }
        return new EndpointTarget(
                endpointId.substring(0, separator).trim().toUpperCase(),
                endpointId.substring(separator + 1).trim()
        );
    }

    private boolean isMeaningful(JsonNode value) {
        return value != null
                && !value.isNull()
                && !value.isMissingNode()
                && !(value.isObject() && value.isEmpty())
                && !(value.isArray() && value.isEmpty())
                && !(value.isTextual() && value.asText().isBlank());
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.isObject() || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        return node.get(field).asText();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String firstNonNull(String first, String second) {
        return first == null ? second : first;
    }

    private record EndpointTarget(String method, String path) {
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

