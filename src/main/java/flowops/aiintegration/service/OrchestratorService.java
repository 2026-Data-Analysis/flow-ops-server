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
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorService {

    private final AiClient aiClient;

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
                        textOrNull(ep, "endpoint_id"),
                        textOrNull(ep, "method"),
                        textOrNull(ep, "path"),
                        null,
                        ep.get("request_body_schema"),
                        ep.get("response_schema"),
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
                new flowops.integration.ai.AiAgentContracts.EnvironmentPayload(null, envName, baseUrl, null),
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

        List<ScenarioEndpointPayload> endpoints = new ArrayList<>();
        JsonNode inventory = ctx.get("api_inventory");
        if (inventory != null && inventory.has("endpoints")) {
            for (JsonNode ep : inventory.get("endpoints")) {
                endpoints.add(new ScenarioEndpointPayload(
                        textOrNull(ep, "endpoint_id"),
                        textOrNull(ep, "path"),
                        textOrNull(ep, "method"),
                        textOrNull(ep, "summary"),
                        null,
                        null,
                        ep.get("request_body_schema"),
                        ep.get("response_schema"),
                        ep.has("auth") ? new ScenarioAuthPayload(
                                textOrNull(ep.get("auth"), "type"), null) : null,
                        null
                ));
            }
        }

        ScenarioGenerateRequest aiReq = new ScenarioGenerateRequest(
                projectId,
                "STANDARD",
                request.userPrompt(),
                new ScenarioApiInventoryPayload(projectId, endpoints),
                List.of(),
                5,
                10
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
}
