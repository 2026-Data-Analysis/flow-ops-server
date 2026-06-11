package flowops.integration.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public final class AiAgentContracts {

    private AiAgentContracts() {
    }

    @Schema(description = "AI 연동 공통 프로젝트 정보")
    public record ProjectPayload(
            String projectId,
            String appId,
            String appName
    ) {
    }

    @Schema(description = "AI 연동 공통 실행 환경 정보")
    public record EnvironmentPayload(
            String environmentId,
            String name,
            String baseUrl,
            String defaultTestLevel,
            String authType,
            JsonNode authConfig,
            JsonNode headers
    ) {
    }

    @Schema(description = "AI 연동 공통 메타데이터")
    public record MetadataPayload(
            String language,
            LocalDateTime createdAt,
            String source
    ) {
    }

    @Schema(description = "AI 요청에 전달되는 API 엔드포인트 정보. endpoint_id와 스키마 필드는 AI 서버 계약 필드명을 사용합니다.")
    public record ApiPayload(
            @JsonProperty("endpoint_id")
            String endpoint_id,
            String method,
            String path,
            String domainTag,
            @JsonProperty("request_body_schema")
            JsonNode request_body_schema,
            @JsonProperty("response_schema")
            JsonNode response_schema,
            Boolean authRequired,
            Boolean deprecated,
            String businessCriticality
    ) {
    }

    @Schema(description = "테스트 케이스 생성 요청 컨텍스트")
    public record TestGenerationContext(
            String generationId,
            String mode,
            String testLevel,
            Double currentCoverage,
            Double targetCoverage,
            String contextSummary
    ) {
    }

    @Schema(description = "기존 테스트 케이스 요약 정보")
    public record ExistingTestCasePayload(
            String testCaseId,
            String apiId,
            String name,
            String type,
            String testLevel,
            JsonNode requestSpec,
            JsonNode expectedSpec,
            JsonNode assertionSpec
    ) {
    }

    @Schema(description = "Test case generator API payload")
    public record TestCaseApiPayload(
            String apiId,
            String method,
            String path,
            String domainTag,
            @JsonProperty("request_body_schema")
            JsonNode requestSchema,
            @JsonProperty("response_schema")
            JsonNode responseSchema,
            List<Integer> expectedStatusCodes,
            List<Integer> errorStatusCodes,
            List<String> errorCodes,
            Boolean authRequired,
            Boolean deprecated
    ) {
    }

    @Schema(description = "실패 기반 테스트 생성을 위한 실패 컨텍스트")
    public record FailureContextPayload(
            Long executionId,
            Long stepId,
            Integer statusCode,
            String requestBody,
            String responseBody,
            String errorMessage,
            String expected,
            String actual
    ) {
    }

    @Schema(description = "테스트 케이스 생성 AI 에이전트 요청")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestCaseGeneratorRequest(
            String agent,
            String requestId,
            String requestedBy,
            ProjectPayload project,
            EnvironmentPayload environment,
            MetadataPayload metadata,
            TestGenerationContext generationContext,
            List<TestCaseApiPayload> apis,
            List<TestCaseApiPayload> domainApis,
            List<ExistingTestCasePayload> existingTestCases,
            FailureContextPayload failureContext
    ) {
    }

    @Schema(description = "테스트 케이스 생성 AI 에이전트가 반환한 초안")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestCaseDraftPayload(
            String apiId,
            @JsonProperty("endpoint_id")
            String endpoint_id,
            String title,
            String description,
            String type,
            @JsonProperty("risk_level")
            String risk_level,
            String userRole,
            String stateCondition,
            String dataVariant,
            @JsonProperty("execution_endpoint")
            String execution_endpoint,
            @JsonProperty("execution_method")
            String execution_method,
            JsonNode requestSpec,
            JsonNode expectedSpec,
            JsonNode assertionSpec,
            boolean duplicate
    ) {
    }

    @Schema(description = "테스트 케이스 생성 AI 에이전트 응답")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TestCaseGeneratorResponse(
            String requestId,
            String generationId,
            List<TestCaseDraftPayload> drafts
    ) {
    }

    @Schema(description = "시나리오 생성 요청 컨텍스트. user_intent와 mode는 AI 서버 계약 필드명을 사용합니다.")
    public record ScenarioContextPayload(
            Long appId,
            @JsonProperty("user_intent")
            String user_intent,
            String mode,
            String testLevel,
            String businessDomain
    ) {
    }

    @Schema(description = "기존 시나리오 요약 정보")
    public record ExistingScenarioPayload(
            Long scenarioId,
            String name,
            String type,
            List<ExistingScenarioStepPayload> steps
    ) {
    }

    @Schema(description = "기존 시나리오 스텝 요약 정보")
    public record ExistingScenarioStepPayload(
            Integer stepOrder,
            Long apiId,
            String label
    ) {
    }

    @Schema(description = "기존 시나리오 요약 (중복 방지용)")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExistingScenarioSummary(
            String name,
            @JsonProperty("step_api_ids")
            List<Long> step_api_ids
    ) {
    }

    @Schema(description = "시나리오 빌더 AI 에이전트 요청")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScenarioGenerateRequest(
            @JsonProperty("project_id")
            String project_id,
            String mode,
            @JsonProperty("user_intent")
            String user_intent,
            @JsonProperty("api_inventory")
            ScenarioApiInventoryPayload api_inventory,
            EnvironmentPayload environment,
            @JsonProperty("existing_test_cases")
            List<ScenarioExistingTestCasePayload> existing_test_cases,
            @JsonProperty("existing_scenarios")
            List<ExistingScenarioSummary> existing_scenarios,
            @JsonProperty("max_scenarios")
            Integer max_scenarios,
            @JsonProperty("max_steps_per_scenario")
            Integer max_steps_per_scenario
    ) {
    }

    public record ScenarioApiInventoryPayload(
            @JsonProperty("project_id")
            String project_id,
            List<ScenarioEndpointPayload> endpoints
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScenarioEndpointPayload(
            @JsonProperty("endpoint_id")
            String endpoint_id,
            String path,
            String method,
            String summary,
            String description,
            JsonNode parameters,
            ScenarioAuthPayload auth,
            @JsonProperty("request_body_schema")
            JsonNode request_body_schema,
            @JsonProperty("response_schema")
            JsonNode response_schema,
            List<Integer> expectedStatusCodes,
            List<Integer> errorStatusCodes,
            List<String> errorCodes,
            List<String> tags
    ) {
    }

    public record ScenarioAuthPayload(
            String type,
            String location
    ) {
    }

    public record ScenarioExistingTestCasePayload(
            @JsonProperty("test_case_id")
            String test_case_id,
            @JsonProperty("endpoint_id")
            String endpoint_id,
            String name,
            String type,
            String description,
            @JsonProperty("risk_level")
            String testLevel,
            JsonNode requestSpec,
            JsonNode expectedSpec,
            JsonNode assertionSpec,
            @JsonProperty("expected_status_code")
            Integer expected_status_code
    ) {
    }

    @Schema(description = "시나리오 빌더 AI 에이전트 응답")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScenarioGenerateResponse(
            String requestId,
            String generationId,
            Boolean success,
            ScenarioGenerateDataPayload data,
            @JsonProperty("error_code")
            String error_code,
            @JsonProperty("error_message")
            String error_message,
            @JsonProperty("trace_id")
            String trace_id
    ) {
    }

    public record ScenarioGenerateDataPayload(
            List<ScenarioPayload> scenarios,
            @JsonProperty("used_endpoint_ids")
            List<String> used_endpoint_ids
    ) {
    }

    public record ScenarioPayload(
            @JsonProperty("scenario_id")
            String scenario_id,
            String name,
            String description,
            String type,
            List<ScenarioStepPayload> steps,
            MetaPayload meta
    ) {
    }

    @Schema(description = "AI 추천 메타데이터")
    public record MetaPayload(
            String rationale,
            @JsonProperty("coverage_gap")
            String coverage_gap,
            @JsonProperty("estimated_risk")
            String estimated_risk
    ) {
    }

    @Schema(description = "AI가 반환한 시나리오 스텝. PDF 명세 기준 camelCase 필드 포함.")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScenarioStepPayload(
            @JsonProperty("step_id")
            String step_id,
            String ref,
            Integer order,
            @JsonProperty("chained_variables")
            JsonNode chained_variables,
            String apiId,
            String title,
            String description,
            String type,
            String userRole,
            String stateCondition,
            String dataVariant,
            @JsonProperty("execution_endpoint")
            String execution_endpoint,
            @JsonProperty("execution_method")
            String execution_method,
            JsonNode requestSpec,
            JsonNode expectedSpec,
            JsonNode assertionSpec,
            boolean duplicate,
            @JsonProperty("static_payload")
            JsonNode static_payload,
            @JsonProperty("static_params")
            JsonNode static_params,
            @JsonProperty("expected_status_code")
            Integer expected_status_code,
            @JsonProperty("expected_assertions")
            List<String> expected_assertions
    ) {
    }

    @Schema(description = "검증 assertion 결과")
    public record AssertionPayload(
            String name,
            String expected,
            String actual,
            boolean passed
    ) {
    }

    @Schema(description = "로그 분석용 검증 실패 정보")
    public record ValidationPayload(
            String errorMessage,
            String expected,
            String actual,
            List<AssertionPayload> assertions
    ) {
    }

    @Schema(description = "로그 분석용 HTTP 요청/응답 정보")
    public record HttpPayload(
            String method,
            String path,
            String status,
            Integer statusCode,
            Long durationMs,
            JsonNode requestBody,
            JsonNode responseBody
    ) {
    }

    @Schema(description = "로그 분석 요청 컨텍스트")
    public record LogContextPayload(
            Long executionId,
            Long stepId,
            String executionName,
            String stepName,
            String environment,
            String testLevel,
            LocalDateTime timestamp
    ) {
    }

    @Schema(description = "분석 대상 주변 로그 요약")
    public record NearbyLogPayload(
            Long stepId,
            String stepName,
            String status,
            Long durationMs
    ) {
    }

    @Schema(description = "로그 분석 AI 에이전트 요청")
    public record LogAnalysisRequest(
            String agent,
            String requestId,
            String requestedBy,
            ProjectPayload project,
            EnvironmentPayload environment,
            MetadataPayload metadata,
            LogContextPayload logContext,
            HttpPayload http,
            ValidationPayload validation,
            List<NearbyLogPayload> nearbyLogs
    ) {
    }

    @Schema(description = "로그 분석 AI 에이전트 응답")
    public record LogAnalysisResponse(
            String diagnosis,
            String failureCategory,
            String severity,
            double confidence,
            List<String> likelyCauses,
            List<String> recommendedActions,
            ReproductionPayload reproduction,
            List<SuggestedTestCasePayload> suggestedTestCases
    ) {
    }

    @Schema(description = "실패 재현 요청 정보")
    public record ReproductionPayload(
            String method,
            String path,
            JsonNode body,
            Integer expectedStatusCode
    ) {
    }

    @Schema(description = "로그 분석 결과 기반 추천 테스트 케이스")
    public record SuggestedTestCasePayload(
            String title,
            String type,
            String expectedSpec
    ) {
    }

    @Schema(description = "장애 리포트용 실행 요약")
    public record ExecutionPayload(
            Long executionId,
            String name,
            String status,
            String environment,
            String testLevel,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Integer totalCount,
            Integer passedCount,
            Integer failedCount,
            String summary
    ) {
    }

    @Schema(description = "장애 리포트용 실패 스텝 정보")
    public record FailedStepPayload(
            Long stepId,
            String stepName,
            String method,
            String path,
            Integer statusCode,
            Long durationMs,
            String requestBody,
            String responseBody,
            String errorMessage,
            String expected,
            String actual
    ) {
    }

    @Schema(description = "장애 리포트 요청 컨텍스트")
    public record ReportContextPayload(
            Long projectId,
            String targetAudience,
            String incident,
            String additionalContext
    ) {
    }

    @Schema(description = "장애 리포트 AI 에이전트 요청")
    public record ErrorReportRequest(
            String agent,
            String requestId,
            String requestedBy,
            ProjectPayload project,
            EnvironmentPayload environment,
            MetadataPayload metadata,
            ReportContextPayload reportContext,
            ExecutionPayload execution,
            List<FailedStepPayload> failedSteps,
            LogAnalysisResponse logAnalysis
    ) {
    }

    @Schema(description = "장애 리포트 AI 에이전트 응답")
    public record ErrorReportResponse(
            String title,
            String summary,
            String impact,
            String rootCauseHypothesis,
            List<String> nextActions,
            List<String> recommendedChannels,
            String customerMessage,
            String internalNotes,
            String severity,
            Double confidence
    ) {
    }

    @Schema(description = "테스트 위험도 분류 요청 컨텍스트")
    public record ClassificationContextPayload(
            String source,
            String defaultTestLevel,
            String policyVersion,
            String goal
    ) {
    }

    @Schema(description = "위험도 분류 대상 테스트 후보")
    public record CandidateTestCasePayload(
            String candidateId,
            String title,
            String description,
            String type,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec,
            Boolean generatedFromFailure,
            String failureCategory,
            Integer previousFailureCount,
            Long historicalAvgDurationMs,
            Boolean coverageGap
    ) {
    }

    @Schema(description = "테스트 위험도 분류 AI 에이전트 요청")
    public record TestStrategyClassifierRequest(
            String agent,
            String requestId,
            String requestedBy,
            ProjectPayload project,
            EnvironmentPayload environment,
            MetadataPayload metadata,
            ClassificationContextPayload classificationContext,
            ApiPayload api,
            List<CandidateTestCasePayload> candidateTestCases
    ) {
    }

    @Schema(description = "분류된 테스트 초안의 테스트 레벨")
    public record ClassifiedDraftPayload(
            String testLevel
    ) {
    }

    @Schema(description = "테스트 위험도 분류 AI 에이전트 응답")
    public record TestStrategyClassifierResponse(
            List<ClassifiedDraftPayload> drafts
    ) {
    }

    @Schema(description = "Orchestrator agent chat request")
    public record OrchestratorChatRequest(
            @JsonProperty("project_id")
            String project_id,
            @JsonProperty("user_prompt")
            String user_prompt,
            JsonNode context
    ) {
    }

    @Schema(description = "Orchestrator agent chat response")
    public record OrchestratorChatResponse(
            boolean success,
            OrchestratorChatDataPayload data,
            @JsonProperty("error_code")
            String error_code,
            @JsonProperty("error_message")
            String error_message,
            @JsonProperty("trace_id")
            String trace_id
    ) {
    }

    public record OrchestratorChatDataPayload(
            @JsonProperty("dispatched_agents")
            List<String> dispatched_agents,
            @JsonProperty("agent_results")
            List<OrchestratorAgentResultPayload> agent_results,
            String summary
    ) {
    }

    public record OrchestratorAgentResultPayload(
            @JsonProperty("agent_type")
            String agent_type,
            boolean success,
            JsonNode data,
            @JsonProperty("error_message")
            String error_message
    ) {
    }

    @Schema(description = "Incident 분석 로그 엔트리")
    public record IncidentLogEntryPayload(
            String timestamp,
            String level,
            String message,
            String logger,
            @JsonProperty("stack_trace")
            String stack_trace,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            com.fasterxml.jackson.databind.JsonNode extra
    ) {
    }

    @Schema(description = "Incident 분석 실패 컨텍스트")
    public record IncidentFailureContextPayload(
            @JsonProperty("test_case_id")
            String test_case_id,
            String endpoint,
            @JsonProperty("expected_status")
            Integer expected_status,
            @JsonProperty("actual_status")
            Integer actual_status,
            @JsonProperty("request_body")
            com.fasterxml.jackson.databind.JsonNode request_body,
            @JsonProperty("response_body")
            com.fasterxml.jackson.databind.JsonNode response_body,
            @JsonProperty("error_message")
            String error_message
    ) {
    }

    @Schema(description = "Incident 분석 AI 에이전트 요청")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IncidentAnalyzeRequest(
            @JsonProperty("project_id")
            String project_id,
            @JsonProperty("service_name")
            String service_name,
            @JsonProperty("occurred_at")
            String occurred_at,
            @JsonProperty("raw_log")
            String raw_log,
            @JsonProperty("log_entries")
            java.util.List<IncidentLogEntryPayload> log_entries,
            @JsonProperty("failure_context")
            IncidentFailureContextPayload failure_context
    ) {
    }

    @Schema(description = "Incident 분석 원인")
    public record IncidentRootCausePayload(
            String summary,
            String severity,
            @JsonProperty("suggested_fix")
            String suggested_fix,
            java.util.List<String> evidence
    ) {
    }

    @Schema(description = "Incident 분석 데이터")
    public record IncidentAnalyzeDataPayload(
            @JsonProperty("root_causes")
            java.util.List<IncidentRootCausePayload> root_causes,
            @JsonProperty("internal_report")
            String internal_report,
            @JsonProperty("external_notice")
            String external_notice
    ) {
    }

    @Schema(description = "Incident 분석 AI 에이전트 응답")
    public record IncidentAnalyzeResponse(
            boolean success,
            IncidentAnalyzeDataPayload data,
            @JsonProperty("error_code")
            String error_code,
            @JsonProperty("error_message")
            String error_message,
            @JsonProperty("trace_id")
            String trace_id
    ) {
    }
}
