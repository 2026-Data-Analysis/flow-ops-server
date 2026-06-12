package flowops.aiintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.dto.request.OrchestratorDispatchRequest;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.AgentResult;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.IncidentAgentData;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.IncidentAgentData.RootCause;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.OrchestratorDispatchData;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.ScenarioAgentData;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.ScenarioResult;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.ScenarioStepResult;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.TestCaseAgentData;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeDataPayload;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeRequest;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeResponse;
import flowops.integration.ai.AiAgentContracts.IncidentRootCausePayload;
import flowops.integration.ai.AiAgentContracts.LogAnalysisRequest;
import flowops.integration.ai.AiAgentContracts.LogAnalysisResponse;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.ReportContextPayload;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioStepPayload;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseDraftPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationApiSelection;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class OrchestratorService {

    private final AiClient aiClient;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final AppService appService;
    private final ApiEndpointService apiEndpointService;
    private final TestGenerationRepository testGenerationRepository;
    private final TestGenerationApiSelectionRepository selectionRepository;
    private final GeneratedTestCaseDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrchestratorDispatchResponse dispatch(OrchestratorDispatchRequest request) {
        String traceId = UUID.randomUUID().toString();
        JsonNode ctx = request.context();

        if (usesPromptAwareRouting()) {
            return dispatchByPromptAndContext(request, traceId, ctx);
        }

        try {
            if (ctx != null && ctx.has("service_name")) {
                return dispatchIncident(request, traceId);
            } else if (ctx != null && (ctx.has("base_url") || ctx.has("env_name"))) {
                return dispatchTestCase(request, traceId);
            } else if (ctx != null && ctx.has("api_inventory")) {
                return dispatchScenario(request, traceId);
            } else {
                return error(traceId, "INVALID_CONTEXT", "context 필드에 service_name, base_url/env_name, api_inventory 중 하나가 필요합니다.");
            }
        } catch (Exception e) {
            log.error("Orchestrator dispatch failed. traceId={}", traceId, e);
            return error(traceId, "AGENT_ERROR", e.getMessage());
        }
    }

    private boolean usesPromptAwareRouting() {
        return true;
    }

    private OrchestratorDispatchResponse dispatchByPromptAndContext(
            OrchestratorDispatchRequest request,
            String traceId,
            JsonNode ctx
    ) {
        String projectId = request.projectId();
        String prompt = request.userPrompt();
        try {
            if (hasText(ctx, "raw_log")) {
                log.info("Orchestrator dispatch selected. projectId={}, agentType=incident, reason=raw_log_present", projectId);
                return dispatchIncident(request, traceId);
            }
            if (promptLooksLikeIncident(prompt)) {
                if (!hasText(ctx, "raw_log")) {
                    return error(traceId, "MISSING_RAW_LOG", "로그 분석을 위해 raw_log가 필요합니다.");
                }
                log.info("Orchestrator dispatch selected. projectId={}, agentType=incident, reason=incident_prompt", projectId);
                return dispatchIncident(request, traceId);
            }
            if (promptLooksLikeScenario(prompt)) {
                if (!hasObject(ctx, "api_inventory")) {
                    return error(traceId, "MISSING_API_INVENTORY", "시나리오 생성을 위해 api_inventory가 필요합니다.");
                }
                if (inventoryEndpoints(ctx).isEmpty()) {
                    return error(traceId, "EMPTY_API_INVENTORY", "api_inventory.endpoints가 비어 있어 시나리오를 생성할 수 없습니다.");
                }
                log.info("Orchestrator dispatch selected. projectId={}, agentType=scenario, reason=scenario_prompt_with_api_inventory", projectId);
                return dispatchScenario(request, traceId);
            }
            if (hasObject(ctx, "api_inventory") && promptLooksLikeTestcase(prompt)) {
                log.info("Orchestrator dispatch selected. projectId={}, agentType=testcase, reason=testcase_prompt_with_api_inventory", projectId);
                return dispatchTestCase(request, traceId);
            }
            if (ctx != null && (ctx.has("base_url") || ctx.has("env_name"))) {
                log.info("Orchestrator dispatch selected. projectId={}, agentType=testcase, reason=environment_context", projectId);
                return dispatchTestCase(request, traceId);
            }
            if (hasObject(ctx, "api_inventory")) {
                log.info("Orchestrator dispatch selected. projectId={}, agentType=testcase, reason=api_inventory_default", projectId);
                return dispatchTestCase(request, traceId);
            }
            return error(traceId, "INVALID_CONTEXT", "context 필드에 raw_log 또는 api_inventory가 필요합니다.");
        } catch (Exception e) {
            log.error("Orchestrator dispatch failed. traceId={}", traceId, e);
            return error(traceId, "AGENT_ERROR", e.getMessage());
        }
    }

    private OrchestratorDispatchResponse dispatchIncident(OrchestratorDispatchRequest request, String traceId) {
        JsonNode ctx = request.context();
        String serviceName = textOrNull(ctx, "service_name");
        String occurredAt = textOrNull(ctx, "occurred_at");
        String rawLog = textOrNull(ctx, "raw_log");
        String projectId = request.projectId() != null ? request.projectId() : "unknown";

        if (usesPromptAwareRouting()) {
            IncidentAnalyzeResponse response = aiClient.analyzeIncident(new IncidentAnalyzeRequest(
                    projectId,
                    serviceName,
                    occurredAt,
                    rawLog,
                    null,
                    null
            ));
            IncidentAgentData agentData = normalizeIncidentData(response == null ? null : response.data());
            boolean success = response == null || response.success();
            int rootCauseCount = agentData.rootCauses() == null ? 0 : agentData.rootCauses().size();
            log.info("Incident agent returned. success={}, rootCauseCount={}, traceId={}",
                    success,
                    rootCauseCount,
                    response == null ? traceId : response.trace_id());

            List<AgentResult> results = List.of(new AgentResult(
                    "incident",
                    success,
                    success ? agentData : null,
                    success ? null : response == null ? "Incident agent returned no response." : response.error_message()
            ));
            String summary = success ? incidentSummary(agentData) : response.error_message();
            return new OrchestratorDispatchResponse(
                    success,
                    new OrchestratorDispatchData(List.of("incident"), results, summary),
                    success ? null : response == null ? "INCIDENT_AGENT_ERROR" : response.error_code(),
                    success ? null : response == null ? "Incident agent returned no response." : response.error_message(),
                    traceId
            );
        }

        LogAnalysisRequest logReq = new LogAnalysisRequest(
                "log-analyzer",
                UUID.randomUUID().toString(),
                "orchestrator",
                new ProjectPayload(projectId, null, serviceName),
                null,
                new MetadataPayload("ko", LocalDateTime.now(), "orchestrator"),
                null, null, null, null
        );

        LogAnalysisResponse logRes = aiClient.analyzeLog(logReq);

        ErrorReportRequest reportReq = new ErrorReportRequest(
                "error-reporter",
                UUID.randomUUID().toString(),
                "orchestrator",
                new ProjectPayload(projectId, null, serviceName),
                null,
                new MetadataPayload("ko", LocalDateTime.now(), "orchestrator"),
                new ReportContextPayload(null, "both", rawLog, occurredAt),
                null, null, logRes
        );

        ErrorReportResponse reportRes = aiClient.generateErrorReport(reportReq);

        List<RootCause> rootCauses = buildRootCauses(logRes, reportRes);
        IncidentAgentData agentData = new IncidentAgentData(
                rootCauses,
                reportRes != null ? reportRes.internalNotes() : null,
                reportRes != null ? reportRes.customerMessage() : null
        );

        List<AgentResult> results = List.of(new AgentResult("incident", true, agentData, null));
        String summary = reportRes != null ? reportRes.summary() : "로그 분석이 완료되었습니다.";

        return new OrchestratorDispatchResponse(
                true,
                new OrchestratorDispatchData(List.of("incident"), results, summary),
                null, null, traceId
        );
    }

    private OrchestratorDispatchResponse dispatchTestCase(OrchestratorDispatchRequest request, String traceId) {
        JsonNode ctx = request.context();
        String projectId = request.projectId() != null ? request.projectId() : "unknown";
        String baseUrl = textOrNull(ctx, "base_url");
        String envName = textOrNull(ctx, "env_name");
        App app = appService.getApp(parseRequiredAppId(projectId));

        List<TestCaseApiPayload> apis = new ArrayList<>();
        Map<String, ApiEndpoint> responseApiIdToEndpoint = new LinkedHashMap<>();
        JsonNode inventory = ctx.get("api_inventory");
        if (inventory != null && inventory.has("endpoints")) {
            for (JsonNode ep : inventory.get("endpoints")) {
                ApiEndpoint endpoint = resolveEndpoint(app.getId(), ep);
                registerEndpoint(responseApiIdToEndpoint, ep, endpoint);
                apis.add(new TestCaseApiPayload(
                        endpointId(ep, endpoint),
                        endpoint.getMethod().name(),
                        endpoint.getPath(),
                        null,
                        ep.get("request_body_schema"),
                        ep.get("response_schema"),
                        integerArray(ep, "expected_status_codes"),
                        integerArray(ep, "error_status_codes"),
                        stringArray(ep, "error_codes"),
                        null,
                        null
                ));
            }
        }

        TestGeneration generation = testGenerationRepository.save(TestGeneration.builder()
                .app(app)
                .environment(null)
                .status(TestGenerationStatus.PROCESSING)
                .requestedBy("orchestrator")
                .contextSummary(request.userPrompt())
                .existingCount(0)
                .newCount(0)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build());
        responseApiIdToEndpoint.values().stream()
                .distinct()
                .map(endpoint -> TestGenerationApiSelection.builder()
                        .generation(generation)
                        .apiEndpoint(endpoint)
                        .build())
                .forEach(selectionRepository::save);

        TestCaseGeneratorRequest aiReq = new TestCaseGeneratorRequest(
                "testcase-generator",
                UUID.randomUUID().toString(),
                "orchestrator",
                new ProjectPayload(projectId, String.valueOf(app.getId()), app.getName()),
                new flowops.integration.ai.AiAgentContracts.EnvironmentPayload(
                        null,
                        envName,
                        baseUrl,
                        null,
                        textOrNull(ctx, "auth_type"),
                        ctx.get("auth_config"),
                        ctx.get("headers")
                ),
                new MetadataPayload("ko", LocalDateTime.now(), "orchestrator"),
                new flowops.integration.ai.AiAgentContracts.TestGenerationContext(
                        String.valueOf(generation.getId()), "FROM_SCRATCH", null, null, null, request.userPrompt()),
                apis,
                apis,
                List.of(),
                null
        );

        List<GeneratedTestCaseDraft> savedDrafts;
        try {
            TestCaseGeneratorResponse res = aiClient.generateTestCaseDrafts(aiReq);
            savedDrafts = saveGeneratedDrafts(generation, res, responseApiIdToEndpoint);
            int duplicateCount = (int) savedDrafts.stream().filter(GeneratedTestCaseDraft::isDuplicate).count();
            generation.markCompleted(savedDrafts.size() - duplicateCount, savedDrafts.size() - duplicateCount, duplicateCount, null);
        } catch (Exception exception) {
            generation.markFailed();
            throw exception;
        }

        TestCaseAgentData agentData = new TestCaseAgentData(
                generation.getId(),
                savedDrafts.stream().map(GeneratedTestCaseDraftResponse::from).toList()
        );
        List<AgentResult> results = List.of(new AgentResult("testcase", true, agentData, null));
        int count = savedDrafts.size();

        return new OrchestratorDispatchResponse(
                true,
                new OrchestratorDispatchData(List.of("testcase"), results,
                        count + "개의 테스트 케이스 초안이 생성되었습니다."),
                null, null, traceId
        );
    }

    private List<GeneratedTestCaseDraft> saveGeneratedDrafts(
            TestGeneration generation,
            TestCaseGeneratorResponse response,
            Map<String, ApiEndpoint> responseApiIdToEndpoint
    ) {
        if (response == null || response.drafts() == null) {
            return List.of();
        }
        List<GeneratedTestCaseDraft> savedDrafts = new ArrayList<>();
        for (TestCaseDraftPayload draft : response.drafts()) {
            // 에이전트가 내려준 draft별 type/risk_level 원본 로그 (테스트 레벨 매핑 검증용)
            log.info("[Orchestrator dispatch testcase draft] title='{}' type='{}' risk_level(raw from agent)='{}'",
                    draft.title(), draft.type(), draft.risk_level());
            ApiEndpoint endpoint = resolveDraftEndpoint(draft, responseApiIdToEndpoint);
            savedDrafts.add(draftRepository.save(GeneratedTestCaseDraft.builder()
                    .generation(generation)
                    .apiEndpoint(endpoint)
                    .title(defaultIfBlank(draft.title(), endpoint.getMethod() + " " + endpoint.getPath()))
                    .description(draft.description())
                    .type(defaultIfBlank(draft.type(), "HAPPY_PATH"))
                    .riskLevel(draft.risk_level())
                    .userRole(draft.userRole())
                    .stateCondition(draft.stateCondition())
                    .dataVariant(draft.dataVariant())
                    .requestSpec(jsonToStorageText(mergeExecutionTarget(
                            draft.requestSpec(),
                            draft.execution_endpoint(),
                            draft.execution_method(),
                            endpoint
                    )))
                    .expectedSpec(jsonToStorageText(draft.expectedSpec()))
                    .assertionSpec(jsonToStorageText(draft.assertionSpec()))
                    .duplicate(draft.duplicate())
                    .selectedForSave(false)
                    .createdAt(LocalDateTime.now())
                    .build()));
        }
        return savedDrafts;
    }

    private ApiEndpoint resolveDraftEndpoint(TestCaseDraftPayload draft, Map<String, ApiEndpoint> responseApiIdToEndpoint) {
        ApiEndpoint endpoint = firstEndpoint(responseApiIdToEndpoint, draft.apiId(), draft.endpoint_id());
        if (endpoint != null) {
            return endpoint;
        }
        throw new ApiException(
                ErrorCode.EXTERNAL_SERVICE_ERROR,
                "AI response did not include a resolvable apiId or endpoint_id."
        );
    }

    private ApiEndpoint firstEndpoint(Map<String, ApiEndpoint> endpoints, String... keys) {
        for (String key : keys) {
            if (key != null && endpoints.containsKey(key)) {
                return endpoints.get(key);
            }
        }
        return null;
    }

    private JsonNode mergeExecutionTarget(JsonNode requestSpec, String executionEndpoint, String executionMethod, ApiEndpoint endpoint) {
        ObjectNode target = requestSpec != null && requestSpec.isObject()
                ? requestSpec.deepCopy()
                : objectMapper.createObjectNode();
        String resolvedEndpoint = defaultIfBlank(executionEndpoint, endpoint.getPath());
        String resolvedMethod = defaultIfBlank(executionMethod, endpoint.getMethod().name());
        if (!target.has("endpoint") && !target.has("path")) {
            target.put("endpoint", resolvedEndpoint);
        }
        if (!target.has("method") && !target.has("httpMethod") && !target.has("http_method")) {
            target.put("method", resolvedMethod.toUpperCase());
        }
        if (requestSpec != null
                && !requestSpec.isNull()
                && !requestSpec.isMissingNode()
                && !requestSpec.isObject()
                && !target.has("body")) {
            target.set("body", requestSpec);
        }
        return target;
    }

    private String jsonToStorageText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return value.toString();
        }
    }

    private void registerEndpoint(Map<String, ApiEndpoint> endpoints, JsonNode payload, ApiEndpoint endpoint) {
        putEndpoint(endpoints, textOrNull(payload, "endpoint_id"), endpoint);
        putEndpoint(endpoints, textOrNull(payload, "apiId"), endpoint);
        putEndpoint(endpoints, String.valueOf(endpoint.getId()), endpoint);
        putEndpoint(endpoints, endpoint.getMethod().name() + ":" + endpoint.getPath(), endpoint);
    }

    private void putEndpoint(Map<String, ApiEndpoint> endpoints, String key, ApiEndpoint endpoint) {
        if (key != null && !key.isBlank()) {
            endpoints.put(key, endpoint);
        }
    }

    private ApiEndpoint resolveEndpoint(Long appId, JsonNode endpointPayload) {
        Long endpointId = parseLongOrNull(firstText(endpointPayload, "endpoint_id", textOrNull(endpointPayload, "apiId")));
        if (endpointId != null) {
            ApiEndpoint endpoint = apiEndpointService.getApiEndpoint(endpointId);
            if (!Objects.equals(endpoint.getApp().getId(), appId)) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "API endpoint does not belong to the requested app.");
            }
            return endpoint;
        }
        ApiMethod method = parseMethod(textOrNull(endpointPayload, "method"));
        String path = textOrNull(endpointPayload, "path");
        String endpointKey = textOrNull(endpointPayload, "endpoint_id");
        if ((method == null || path == null || path.isBlank()) && endpointKey != null) {
            int separator = endpointKey.indexOf(':');
            if (separator > 0) {
                method = parseMethod(endpointKey.substring(0, separator));
                path = endpointKey.substring(separator + 1);
            }
        }
        if (method == null || path == null || path.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "api_inventory endpoint requires endpoint_id or method/path.");
        }
        return apiEndpointService.findFirstByAppIdAndMethodAndPath(appId, method, path);
    }

    private String endpointId(JsonNode payload, ApiEndpoint endpoint) {
        String endpointId = firstText(payload, "endpoint_id", textOrNull(payload, "apiId"));
        return endpointId == null || endpointId.isBlank()
                ? endpoint.getMethod().name() + ":" + endpoint.getPath()
                : endpointId;
    }

    private Long parseRequiredAppId(String projectId) {
        Long appId = parseLongOrNull(projectId);
        if (appId == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "project_id must be a FlowOps app ID for testcase generation.");
        }
        return appId;
    }

    private Long parseLongOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ApiMethod parseMethod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ApiMethod.valueOf(value.trim().toUpperCase());
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private OrchestratorDispatchResponse dispatchScenario(OrchestratorDispatchRequest request, String traceId) {
        JsonNode ctx = request.context();
        String projectId = request.projectId() != null ? request.projectId() : "unknown";
        String mode = textOrNull(ctx, "mode");
        if (mode == null || mode.isBlank()) {
            mode = request.userPrompt() == null || request.userPrompt().isBlank() ? "RECOMMEND" : "NATURAL_LANGUAGE";
        }

        List<ScenarioEndpointPayload> endpoints = new ArrayList<>();
        JsonNode inventory = ctx.get("api_inventory");
        String inventoryProjectId = textOrNull(inventory, "project_id");
        if (inventoryProjectId != null && !inventoryProjectId.isBlank()) {
            projectId = inventoryProjectId;
        }
        if (inventory != null && inventory.has("endpoints")) {
            for (JsonNode ep : inventory.get("endpoints")) {
                endpoints.add(new ScenarioEndpointPayload(
                        textOrNull(ep, "endpoint_id"),
                        textOrNull(ep, "path"),
                        textOrNull(ep, "method"),
                        textOrNull(ep, "summary"),
                        textOrNull(ep, "description"),
                        ep.get("parameters"),
                        authPayload(ep.get("auth")),
                        ep.get("requestSchema") == null ? ep.get("request_body_schema") : ep.get("requestSchema"),
                        ep.get("responseSchema") == null ? ep.get("response_schema") : ep.get("responseSchema"),
                        integerArrayWithAlias(ep, "expectedStatusCodes", "expected_status_codes"),
                        integerArrayWithAlias(ep, "errorStatusCodes", "error_status_codes"),
                        stringArrayWithAlias(ep, "errorCodes", "error_codes"),
                        stringArray(ep, "tags")
                ));
            }
        }

        ScenarioGenerateRequest aiReq = new ScenarioGenerateRequest(
                projectId,
                mode,
                "NATURAL_LANGUAGE".equals(mode) ? firstText(ctx, "user_intent", request.userPrompt()) : null,
                new ScenarioApiInventoryPayload(projectId, endpoints),
                environmentPayload(ctx),
                scenarioExistingTestCases(ctx),
                existingScenarioSummariesFromContext(ctx, request.projectId()),
                intOrDefault(ctx, "max_scenarios", 3),
                intOrNull(ctx, "max_steps_per_scenario")
        );

        ScenarioGenerateResponse res = aiClient.buildScenario(aiReq);

        if (usesPromptAwareRouting()) {
            boolean success = res == null || res.success() == null || res.success();
            ScenarioAgentData agentData = success ? normalizeScenarioData(res == null ? null : res.data()) : null;
            int scenarioCount = agentData == null || agentData.scenarios() == null ? 0 : agentData.scenarios().size();
            int usedEndpointCount = agentData == null || agentData.usedEndpointIds() == null ? 0 : agentData.usedEndpointIds().size();
            log.info("Scenario agent returned. success={}, scenarioCount={}, usedEndpointCount={}, traceId={}",
                    success,
                    scenarioCount,
                    usedEndpointCount,
                    res == null ? traceId : res.trace_id());

            List<AgentResult> results = List.of(new AgentResult(
                    "scenario",
                    success,
                    agentData,
                    success ? null : res == null ? "Scenario agent returned no response." : res.error_message()
            ));
            return new OrchestratorDispatchResponse(
                    success,
                    new OrchestratorDispatchData(List.of("scenario"), results,
                            success ? scenarioSummary(agentData) : res == null ? "시나리오 생성에 실패했습니다." : res.error_message()),
                    success ? null : res == null ? "SCENARIO_AGENT_ERROR" : res.error_code(),
                    success ? null : res == null ? "Scenario agent returned no response." : res.error_message(),
                    traceId
            );
        }

        List<AgentResult> results = List.of(new AgentResult("scenario", true, res != null ? res.data() : null, null));
        int count = res != null && res.data() != null && res.data().scenarios() != null
                ? res.data().scenarios().size() : 0;

        return new OrchestratorDispatchResponse(
                true,
                new OrchestratorDispatchData(List.of("scenario"), results,
                        count + "개의 시나리오가 생성되었습니다."),
                null, null, traceId
        );
    }

    private ScenarioAuthPayload authPayload(JsonNode auth) {
        if (auth == null || auth.isNull()) {
            return null;
        }
        if (auth.isBoolean()) {
            return auth.asBoolean()
                    ? new ScenarioAuthPayload("bearer", "header")
                    : new ScenarioAuthPayload("none", null);
        }
        if (auth.isObject()) {
            return new ScenarioAuthPayload(textOrNull(auth, "type"), textOrNull(auth, "location"));
        }
        return null;
    }

    private EnvironmentPayload environmentPayload(JsonNode ctx) {
        JsonNode environment = ctx == null ? null : ctx.get("environment");
        return new EnvironmentPayload(
                firstText(environment, "environmentId", textOrNull(ctx, "environment_id")),
                firstText(environment, "name", textOrNull(ctx, "env_name")),
                firstText(environment, "baseUrl", textOrNull(ctx, "base_url")),
                firstText(environment, "defaultTestLevel", textOrNull(ctx, "default_test_level")),
                firstText(environment, "authType", textOrNull(ctx, "auth_type")),
                environment != null && environment.has("authConfig") ? environment.get("authConfig") : ctx == null ? null : ctx.get("auth_config"),
                environment != null && environment.has("headers") ? environment.get("headers") : ctx == null ? null : ctx.get("headers")
        );
    }

    private IncidentAgentData normalizeIncidentData(IncidentAnalyzeDataPayload data) {
        try {
            List<RootCause> rootCauses = data == null || data.root_causes() == null
                    ? List.of()
                    : data.root_causes().stream()
                    .map(this::toRootCause)
                    .toList();
            return new IncidentAgentData(
                    rootCauses,
                    data == null ? null : data.internal_report(),
                    data == null ? null : data.external_notice()
            );
        } catch (Exception exception) {
            log.warn("Failed to normalize orchestrator incident result. error={}", exception.getMessage());
            return new IncidentAgentData(List.of(), null, null);
        }
    }

    private RootCause toRootCause(IncidentRootCausePayload cause) {
        return new RootCause(
                cause == null ? null : cause.summary(),
                cause == null || cause.evidence() == null ? List.of() : cause.evidence(),
                cause == null ? null : cause.severity(),
                cause == null ? null : cause.suggested_fix()
        );
    }

    private String incidentSummary(IncidentAgentData data) {
        List<RootCause> causes = data == null || data.rootCauses() == null ? List.of() : data.rootCauses();
        if (causes.isEmpty()) {
            return "⚠️ [장애 원인 분석] 완료 — 명확한 원인 후보를 찾지 못했습니다.";
        }
        String mainCause = causes.get(0).summary() == null ? "" : causes.get(0).summary();
        return "✅ [장애 원인 분석] 완료 — 원인 후보 %d건 도출 — 주요 원인: %s"
                .formatted(causes.size(), mainCause);
    }

    private ScenarioAgentData normalizeScenarioData(flowops.integration.ai.AiAgentContracts.ScenarioGenerateDataPayload data) {
        try {
            List<ScenarioResult> scenarios = data == null || data.scenarios() == null
                    ? List.of()
                    : data.scenarios().stream().map(this::normalizeScenario).toList();
            List<String> usedEndpointIds = data == null || data.used_endpoint_ids() == null
                    ? scenarios.stream()
                    .flatMap(scenario -> scenario.steps().stream())
                    .map(ScenarioStepResult::endpointId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList()
                    : data.used_endpoint_ids();
            return new ScenarioAgentData(scenarios, usedEndpointIds);
        } catch (Exception exception) {
            log.warn("Failed to normalize orchestrator scenario result. error={}", exception.getMessage());
            return new ScenarioAgentData(List.of(), List.of());
        }
    }

    private ScenarioResult normalizeScenario(ScenarioPayload scenario) {
        List<ScenarioStepResult> steps = scenario.steps() == null
                ? List.of()
                : scenario.steps().stream().map(this::normalizeScenarioStep).toList();
        JsonNode meta = scenario.meta() == null ? objectMapper.nullNode() : objectMapper.valueToTree(scenario.meta());
        return new ScenarioResult(
                scenario.scenario_id(),
                scenario.name(),
                scenario.description(),
                scenario.type(),
                scenario.test_level(),
                steps,
                meta
        );
    }

    private ScenarioStepResult normalizeScenarioStep(ScenarioStepPayload step) {
        JsonNode requestSpec = step.requestSpec();
        JsonNode expectedSpec = step.expectedSpec();
        String endpointId = firstNonBlank(step.endpoint_id(), step.apiId());
        String name = firstNonBlank(step.name(), step.title());
        JsonNode payload = step.static_payload() != null && !step.static_payload().isNull()
                ? step.static_payload()
                : child(requestSpec, "body");
        JsonNode params = step.static_params() != null && !step.static_params().isNull()
                ? step.static_params()
                : mergedParams(requestSpec);
        Integer expectedStatusCode = step.expected_status_code() != null
                ? step.expected_status_code()
                : intFrom(expectedSpec, "statusCode", "status", "expectedStatusCode", "expected_status_code");
        return new ScenarioStepResult(
                endpointId,
                name,
                payload == null ? objectMapper.nullNode() : payload,
                params == null ? objectMapper.nullNode() : params,
                expectedStatusCode,
                step.expected_assertions() == null ? List.of() : step.expected_assertions(),
                requestSpec,
                expectedSpec,
                step.assertionSpec()
        );
    }

    private String scenarioSummary(ScenarioAgentData data) {
        int scenarioCount = data == null || data.scenarios() == null ? 0 : data.scenarios().size();
        if (scenarioCount == 0) {
            return "⚠️ [시나리오(E2E) 테스트 생성] 완료 — 생성된 시나리오가 없습니다.";
        }
        int usedEndpointCount = data.usedEndpointIds() == null ? 0 : data.usedEndpointIds().size();
        return "✅ [시나리오(E2E) 테스트 생성] 완료 — %d개 시나리오 생성됨 (사용 API %d개)"
                .formatted(scenarioCount, usedEndpointCount);
    }

    private List<ScenarioExistingTestCasePayload> scenarioExistingTestCases(JsonNode ctx) {
        JsonNode node = ctx == null ? null : ctx.get("existing_test_cases");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<ScenarioExistingTestCasePayload> values = new ArrayList<>();
        node.forEach(item -> values.add(objectMapper.convertValue(item, ScenarioExistingTestCasePayload.class)));
        return values;
    }

    private List<ExistingScenarioSummary> existingScenarioSummariesFromContext(JsonNode ctx, String projectId) {
        JsonNode node = ctx == null ? null : ctx.get("existing_scenarios");
        if (node != null && node.isArray()) {
            List<ExistingScenarioSummary> values = new ArrayList<>();
            node.forEach(item -> values.add(objectMapper.convertValue(item, ExistingScenarioSummary.class)));
            return values;
        }
        return existingScenarioSummaries(projectId);
    }

    private JsonNode inventoryEndpoints(JsonNode ctx) {
        JsonNode inventory = ctx == null ? null : ctx.get("api_inventory");
        JsonNode endpoints = inventory == null ? null : inventory.get("endpoints");
        return endpoints != null && endpoints.isArray() ? endpoints : objectMapper.createArrayNode();
    }

    private boolean hasObject(JsonNode node, String field) {
        return node != null && node.has(field) && node.get(field).isObject();
    }

    private boolean hasText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value != null && !value.isBlank();
    }

    private boolean promptLooksLikeIncident(String prompt) {
        String value = normalizedPrompt(prompt).replace("로그인", "");
        return containsAny(value, "로그 분석", "장애 분석", "에러 분석", "원인 분석", "incident", "장애", "로그", "에러", "exception", "error");
    }

    private boolean promptLooksLikeScenario(String prompt) {
        String value = normalizedPrompt(prompt);
        return containsAny(value, "시나리오", "e2e", "end-to-end", "흐름", "사용자 여정", "회원가입 후 로그인", "주문 흐름");
    }

    private boolean promptLooksLikeTestcase(String prompt) {
        String value = normalizedPrompt(prompt);
        return containsAny(value, "테스트케이스", "테스트 케이스", "testcase", "test case", "단건", "api 테스트");
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalizedPrompt(String prompt) {
        return prompt == null ? "" : prompt.toLowerCase();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private JsonNode child(JsonNode node, String field) {
        return node == null || !node.has(field) ? null : node.get(field);
    }

    private JsonNode mergedParams(JsonNode requestSpec) {
        ObjectNode merged = objectMapper.createObjectNode();
        copyObjectFields(merged, child(requestSpec, "pathParams"));
        copyObjectFields(merged, child(requestSpec, "pathParameters"));
        copyObjectFields(merged, child(requestSpec, "path_params"));
        copyObjectFields(merged, child(requestSpec, "queryParams"));
        copyObjectFields(merged, child(requestSpec, "queryParameters"));
        copyObjectFields(merged, child(requestSpec, "query_params"));
        return merged.isEmpty() ? objectMapper.nullNode() : merged;
    }

    private void copyObjectFields(ObjectNode target, JsonNode source) {
        if (source != null && source.isObject()) {
            source.fields().forEachRemaining(entry -> target.set(entry.getKey(), entry.getValue()));
        }
    }

    private Integer intFrom(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    private Integer intOrDefault(JsonNode node, String field, int fallback) {
        Integer value = intOrNull(node, field);
        return value == null ? fallback : value;
    }

    private List<RootCause> buildRootCauses(LogAnalysisResponse logRes, ErrorReportResponse reportRes) {
        List<RootCause> causes = new ArrayList<>();
        if (logRes != null && logRes.likelyCauses() != null) {
            String severity = logRes.severity() != null ? logRes.severity().toUpperCase() : "MEDIUM";
            String suggestedFix = reportRes != null && reportRes.nextActions() != null && !reportRes.nextActions().isEmpty()
                    ? String.join(" / ", reportRes.nextActions()) : null;
            for (String cause : logRes.likelyCauses()) {
                causes.add(new RootCause(
                        cause,
                        logRes.recommendedActions() != null ? logRes.recommendedActions() : List.of(),
                        severity,
                        suggestedFix
                ));
            }
        }
        if (causes.isEmpty() && reportRes != null && reportRes.rootCauseHypothesis() != null) {
            causes.add(new RootCause(
                    reportRes.rootCauseHypothesis(),
                    List.of(),
                    reportRes.severity() != null ? reportRes.severity().toUpperCase() : "MEDIUM",
                    null
            ));
        }
        return causes;
    }

    private OrchestratorDispatchResponse error(String traceId, String errorCode, String errorMessage) {
        return new OrchestratorDispatchResponse(false, null, errorCode, errorMessage, traceId);
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private Integer intOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull() || !node.get(field).canConvertToInt()) {
            return null;
        }
        return node.get(field).asInt();
    }

    private List<Integer> integerArray(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        node.get(field).forEach(value -> {
            if (value.canConvertToInt()) {
                values.add(value.intValue());
            }
        });
        return values;
    }

    private List<Integer> integerArrayWithAlias(JsonNode node, String primaryField, String fallbackField) {
        List<Integer> primary = integerArray(node, primaryField);
        return primary.isEmpty() ? integerArray(node, fallbackField) : primary;
    }

    private List<String> stringArray(JsonNode node, String field) {
        if (node == null || !node.has(field) || !node.get(field).isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.get(field).forEach(value -> {
            if (value.isTextual()) {
                values.add(value.asText());
            }
        });
        return values;
    }

    private List<String> stringArrayWithAlias(JsonNode node, String primaryField, String fallbackField) {
        List<String> primary = stringArray(node, primaryField);
        return primary.isEmpty() ? stringArray(node, fallbackField) : primary;
    }

    private String firstText(JsonNode node, String field, String fallback) {
        String value = textOrNull(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<ExistingScenarioSummary> existingScenarioSummaries(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return List.of();
        }
        try {
            long appId = Long.parseLong(projectId.trim());
            return scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId).stream()
                    .map(scenario -> {
                        // step_api_ids는 api_inventory[].endpoint_id(method:path)와 동일한 문자열이어야
                        // AI 서버에서 dedup 매칭이 된다. 숫자 DB id가 아니라 endpoint_id 문자열로 통일한다.
                        List<String> stepApiIds = scenarioStepRepository
                                .findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                                .stream()
                                .map(step -> {
                                    if (step.getApiInventory() != null) {
                                        return step.getApiInventory().getMethod().name() + ":"
                                                + step.getApiInventory().getEndpointPath();
                                    }
                                    if (step.getApiEndpoint() != null) {
                                        return step.getApiEndpoint().getMethod().name() + ":"
                                                + step.getApiEndpoint().getPath();
                                    }
                                    return null;
                                })
                                .filter(Objects::nonNull)
                                .toList();
                        return new ExistingScenarioSummary(scenario.getName(), stepApiIds);
                    })
                    .toList();
        } catch (NumberFormatException e) {
            return List.of();
        }
    }
}
