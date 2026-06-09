package flowops.aiintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.dto.request.OrchestratorDispatchRequest;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.AgentResult;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.IncidentAgentData;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.IncidentAgentData.RootCause;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.OrchestratorDispatchData;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
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
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorService {

    private final AiClient aiClient;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;

    public OrchestratorDispatchResponse dispatch(OrchestratorDispatchRequest request) {
        String traceId = UUID.randomUUID().toString();
        JsonNode ctx = request.context();

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

    private OrchestratorDispatchResponse dispatchIncident(OrchestratorDispatchRequest request, String traceId) {
        JsonNode ctx = request.context();
        String serviceName = textOrNull(ctx, "service_name");
        String occurredAt = textOrNull(ctx, "occurred_at");
        String rawLog = textOrNull(ctx, "raw_log");
        String projectId = request.projectId() != null ? request.projectId() : "unknown";

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

        List<TestCaseApiPayload> apis = new ArrayList<>();
        JsonNode inventory = ctx.get("api_inventory");
        if (inventory != null && inventory.has("endpoints")) {
            for (JsonNode ep : inventory.get("endpoints")) {
                apis.add(new TestCaseApiPayload(
                        textOrNull(ep, "endpoint_id"),
                        textOrNull(ep, "method"),
                        textOrNull(ep, "path"),
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

        TestCaseGeneratorRequest aiReq = new TestCaseGeneratorRequest(
                "testcase-generator",
                UUID.randomUUID().toString(),
                "orchestrator",
                new ProjectPayload(projectId, null, null),
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
                        null, "FROM_SCRATCH", null, null, null, request.userPrompt()),
                apis,
                List.of(),
                null
        );

        TestCaseGeneratorResponse res = aiClient.generateTestCaseDrafts(aiReq);

        List<AgentResult> results = List.of(new AgentResult("testcase", true, res, null));
        int count = res != null && res.drafts() != null ? res.drafts().size() : 0;

        return new OrchestratorDispatchResponse(
                true,
                new OrchestratorDispatchData(List.of("testcase"), results,
                        count + "개의 테스트 케이스 초안이 생성되었습니다."),
                null, null, traceId
        );
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
                "RECOMMEND".equals(mode) ? List.of() : null,
                existingScenarioSummaries(request.projectId()),
                intOrNull(ctx, "max_scenarios"),
                intOrNull(ctx, "max_steps_per_scenario")
        );

        ScenarioGenerateResponse res = aiClient.buildScenario(aiReq);

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
                        List<Long> stepApiIds = scenarioStepRepository
                                .findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                                .stream()
                                .map(step -> step.getApiInventory() != null
                                        ? step.getApiInventory().getId()
                                        : step.getApiEndpoint() != null ? step.getApiEndpoint().getId() : null)
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
