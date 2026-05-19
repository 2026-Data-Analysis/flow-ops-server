package flowops.scenario.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import flowops.environment.service.EnvironmentService;
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
import java.util.LinkedHashSet;
import java.util.List;
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
    private final EnvironmentService environmentService;
    private final AiClient aiClient;
    private final ExternalServiceProperties externalServiceProperties;
    private final ObjectMapper objectMapper;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public ScenarioDetailResponse create(CreateScenarioRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = request.environmentId() == null ? null : environmentService.getEnvironment(request.environmentId());
        if (environment != null && !environment.getApp().getId().equals(app.getId())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario environment does not belong to the requested app.");
        }
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
        return ScenarioDetailResponse.of(scenario, steps);
    }

    @Transactional(readOnly = true)
    public List<ScenarioSummaryResponse> listByApp(Long appId, Long environmentId, Long repositoryId, String branchName) {
        appService.getApp(appId);
        List<Scenario> scenarios = environmentId == null
                ? scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId)
                : scenarioRepository.findByAppIdAndEnvironmentIdOrderByUpdatedAtDesc(appId, environmentId);
        if (environmentId == null && (repositoryId != null || (branchName != null && !branchName.isBlank()))) {
            scenarios = scenarios.stream()
                    .filter(scenario -> matchesEnvironment(scenario, repositoryId, branchName))
                    .toList();
        }
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
        Environment environment = request.environmentId() == null ? null : environmentService.getEnvironment(request.environmentId());
        List<ApiInventory> inventories = scenarioInventories(app.getId(), request.apiIds());
        List<ApiEndpoint> endpoints = inventories.isEmpty() ? scenarioEndpoints(app.getId(), request.apiIds()) : List.of();
        List<ScenarioEndpointPayload> aiEndpoints = !inventories.isEmpty()
                ? inventories.stream().map(this::toScenarioEndpointPayload).toList()
                : endpoints.stream().map(this::toScenarioEndpointPayload).toList();
        String projectId = projectId(app, inventories);
        log.info("Scenario recommendation payload prepared. appId={}, requestedApiIdCount={}, inventoryCount={}, endpointFallbackCount={}, aiEndpointCount={}, firstEndpointIds={}",
                app.getId(),
                request.apiIds() == null ? 0 : request.apiIds().size(),
                inventories.size(),
                endpoints.size(),
                aiEndpoints.size(),
                aiEndpoints.stream().limit(5).map(ScenarioEndpointPayload::endpoint_id).toList());

        log.info("Calling AI scenario generator. appId={}, environmentId={}, projectId={}, mode={}, apiCount={}, requestedBy={}, mockEnabled={}",
                app.getId(),
                environment == null ? null : environment.getId(),
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
            log.warn("AI scenario generator returned no scenarios. appId={}, environmentId={}, success={}, errorCode={}, errorMessage={}, traceId={}",
                    app.getId(),
                    environment == null ? null : environment.getId(),
                    response == null ? null : response.success(),
                    response == null ? null : response.error_code(),
                    response == null ? null : response.error_message(),
                    response == null ? null : response.trace_id());
            return List.of();
        }

        List<ScenarioRecommendationResponse> recommendations = response.data().scenarios().stream()
                .filter(scenario -> scenario.name() != null && !scenario.name().isBlank())
                .map(scenario -> toRecommendation(scenario, request.scenarioType()))
                .toList();
        log.info("AI scenario generator completed. appId={}, scenarioCount={}, traceId={}",
                app.getId(),
                recommendations.size(),
                response.trace_id());
        return recommendations;
    }

    private List<ScenarioRecommendationResponse> mockRecommendations() {
        return List.of(
                new ScenarioRecommendationResponse("Critical checkout flow", ScenarioType.HAPPY_PATH, "Covers a high-value multi-endpoint business path."),
                new ScenarioRecommendationResponse("Validation guard rails", ScenarioType.EDGE_CASE, "Focuses on required-field and malformed-input failures."),
                new ScenarioRecommendationResponse("Recovery after dependency failure", ScenarioType.FAILURE_RECOVERY, "Models retry and fallback behavior after an upstream error.")
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

    private ScenarioRecommendationResponse toRecommendation(ScenarioPayload scenario, ScenarioType fallbackType) {
        String reason = scenario.meta() == null || scenario.meta().rationale() == null
                ? scenario.description()
                : scenario.meta().rationale();
        return new ScenarioRecommendationResponse(
                scenario.name(),
                fallbackType == null ? ScenarioType.HAPPY_PATH : fallbackType,
                reason
        );
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
            validateInventoryEnvironment(apiInventory, scenario.getEnvironment());
            ScenarioStep saved = scenarioStepRepository.save(ScenarioStep.builder()
                    .scenario(scenario)
                    .stepOrder(step.stepOrder())
                    .apiEndpoint(apiInventory == null ? apiEndpointService.getApiEndpoint(step.apiId()) : endpointForInventory(apiInventory))
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

    private ApiEndpoint endpointForInventory(ApiInventory apiInventory) {
        ApiMethod method = ApiMethod.valueOf(apiInventory.getMethod().name());
        return apiEndpointService.findFirstByMethodAndPath(method, apiInventory.getEndpointPath());
    }

    private boolean matchesEnvironment(Scenario scenario, Long repositoryId, String branchName) {
        if (scenario.getEnvironment() == null) {
            return false;
        }
        boolean repositoryMatches = repositoryId == null
                || (scenario.getEnvironment().getRepositoryInfo() != null
                && scenario.getEnvironment().getRepositoryInfo().getId().equals(repositoryId));
        boolean branchMatches = branchName == null || branchName.isBlank()
                || branchName.equals(scenario.getEnvironment().getBranchName());
        return repositoryMatches && branchMatches;
    }

    private void validateInventoryEnvironment(ApiInventory apiInventory, Environment environment) {
        if (apiInventory == null || environment == null) {
            return;
        }
        boolean repositoryMatches = environment.getRepositoryInfo() == null
                || (apiInventory.getRepositoryInfo() != null
                && apiInventory.getRepositoryInfo().getId().equals(environment.getRepositoryInfo().getId()));
        boolean branchMatches = environment.getBranchName() == null
                || environment.getBranchName().isBlank()
                || environment.getBranchName().equals(apiInventory.getBranchName());
        if (!repositoryMatches || !branchMatches) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario step API inventory does not belong to the scenario environment.");
        }
    }
}
