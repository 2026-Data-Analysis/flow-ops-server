package flowops.aiintegration.controller;

import com.fasterxml.jackson.databind.JsonNode;
import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.service.OrchestratorChatResponseNormalizer;
import flowops.global.response.ApiResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
import flowops.integration.ai.AiAgentContracts.LogAnalysisRequest;
import flowops.integration.ai.AiAgentContracts.LogAnalysisResponse;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeRequest;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeResponse;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatRequest;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierRequest;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CommonApiErrorResponses
@RestController
@RequestMapping("/ai/agents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI 에이전트", description = "테스트 케이스, 시나리오, 로그 분석, 장애 리포트 AI 연동 API")
public class AiAgentController {

    private final AiClient aiClient;
    private final OrchestratorChatResponseNormalizer orchestratorChatResponseNormalizer;

    @PostMapping("/test-cases/generate")
    @Operation(summary = "테스트 케이스 초안 생성", description = "선택한 API와 기존 테스트 정보를 바탕으로 AI 테스트 케이스 초안을 생성합니다.")
    public ApiResponse<TestCaseGeneratorResponse> generateTestCases(
            @Valid @RequestBody TestCaseGeneratorRequest request
    ) {
        return ApiResponse.success(aiClient.generateTestCaseDrafts(request));
    }

    @PostMapping("/scenarios/build")
    @Operation(summary = "API 시나리오 생성", description = "사용자 의도와 API 목록을 바탕으로 실행 가능한 시나리오 스텝을 추천합니다.")
    public ApiResponse<ScenarioGenerateResponse> buildScenario(
            @Valid @RequestBody ScenarioGenerateRequest request
    ) {
        return ApiResponse.success(aiClient.buildScenario(request));
    }

    @PostMapping("/logs/analyze")
    @Operation(summary = "실패 로그 분석", description = "실행 로그, 요청/응답, 검증 결과를 바탕으로 실패 원인과 조치 방안을 분석합니다.")
    public ApiResponse<LogAnalysisResponse> analyzeLog(
            @Valid @RequestBody LogAnalysisRequest request
    ) {
        return ApiResponse.success(aiClient.analyzeLog(request));
    }

    @PostMapping("/error-reports/generate")
    @Operation(summary = "장애 리포트 생성", description = "실행 실패 정보와 로그 분석 결과를 바탕으로 대상 독자에 맞는 장애 리포트를 생성합니다.")
    public ApiResponse<ErrorReportResponse> generateErrorReport(
            @Valid @RequestBody ErrorReportRequest request
    ) {
        return ApiResponse.success(aiClient.generateErrorReport(request));
    }

    @PostMapping("/test-strategy/classify")
    @Operation(summary = "테스트 위험도 분류", description = "생성된 테스트 후보를 SMOKE, SANITY, REGRESSION, FULL 테스트 레벨로 분류합니다.")
    public ApiResponse<TestStrategyClassifierResponse> classifyTestStrategy(
            @Valid @RequestBody TestStrategyClassifierRequest request
    ) {
        return ApiResponse.success(aiClient.classifyTestStrategy(request));
    }

    @PostMapping("/orchestrator/chat")
    @Operation(summary = "Orchestrator chat", description = "사용자 자연어 요청과 컨텍스트를 AI orchestrator로 전달하고, 라우팅된 agent 결과를 반환합니다.")
    public ApiResponse<OrchestratorChatResponse> chatWithOrchestrator(
            @Valid @RequestBody OrchestratorChatRequest request
    ) {
        logOrchestratorChatScope(request);
        OrchestratorChatResponse response = aiClient.chatWithOrchestrator(request);
        return ApiResponse.success(orchestratorChatResponseNormalizer.normalize(request, response));
    }

    @PostMapping("/incidents/analyze")
    @Operation(summary = "Incident 분석", description = "서비스 장애 로그와 컨텍스트를 분석하여 원인 진단 및 장애 보고서를 생성합니다.")
    public ApiResponse<IncidentAnalyzeResponse> analyzeIncident(
            @Valid @RequestBody IncidentAnalyzeRequest request
    ) {
        return ApiResponse.success(aiClient.analyzeIncident(request));
    }

    private void logOrchestratorChatScope(OrchestratorChatRequest request) {
        JsonNode context = request.context();
        JsonNode inventory = context == null ? null : context.get("api_inventory");
        JsonNode endpoints = inventory == null ? null : inventory.get("endpoints");
        JsonNode lookup = context == null ? null : context.get("inventory_lookup");
        int endpointCount = endpoints != null && endpoints.isArray() ? endpoints.size() : 0;
        log.info("Orchestrator chat request scope. projectId={}, userPrompt={}, lookup={}, endpointCount={}, endpoints={}",
                request.project_id(),
                request.user_prompt(),
                lookup,
                endpointCount,
                endpointSummaries(endpoints));
    }

    private String endpointSummaries(JsonNode endpoints) {
        if (endpoints == null || !endpoints.isArray()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(endpoints.size(), 20);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            JsonNode endpoint = endpoints.get(index);
            builder.append(text(endpoint, "method"))
                    .append(' ')
                    .append(text(endpoint, "path"))
                    .append(" id=")
                    .append(text(endpoint, "endpoint_id"))
                    .append(" op=")
                    .append(text(endpoint, "operationId"))
                    .append(" summary=")
                    .append(text(endpoint, "summary"));
        }
        if (endpoints.size() > limit) {
            builder.append(", ... +").append(endpoints.size() - limit);
        }
        return builder.append(']').toString();
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node == null ? null : node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
