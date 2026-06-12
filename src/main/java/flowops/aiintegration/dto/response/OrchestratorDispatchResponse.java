package flowops.aiintegration.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import java.util.List;

public record OrchestratorDispatchResponse(
        boolean success,
        OrchestratorDispatchData data,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("trace_id") String traceId
) {

    public record OrchestratorDispatchData(
            @JsonProperty("dispatched_agents") List<String> dispatchedAgents,
            @JsonProperty("agent_results") List<AgentResult> agentResults,
            String summary
    ) {
    }

    public record AgentResult(
            @JsonProperty("agent_type") String agentType,
            boolean success,
            Object data,
            @JsonProperty("error_message") String errorMessage
    ) {
    }

    public record IncidentAgentData(
            @JsonProperty("root_causes") List<RootCause> rootCauses,
            @JsonProperty("internal_report") String internalReport,
            @JsonProperty("external_notice") String externalNotice
    ) {

        public record RootCause(
                String summary,
                List<String> evidence,
                String severity,
                @JsonProperty("suggested_fix") String suggestedFix
        ) {
        }
    }

    public record TestCaseAgentData(
            Long generationId,
            List<GeneratedTestCaseDraftResponse> drafts
    ) {
    }

    public record ScenarioAgentData(
            List<ScenarioResult> scenarios,
            @JsonProperty("used_endpoint_ids") List<String> usedEndpointIds,
            @JsonProperty("fallback_used") Boolean fallbackUsed,
            @JsonProperty("fallback_reason") String fallbackReason,
            @JsonProperty("fallback_prompt_type") String fallbackPromptType
    ) {
        public ScenarioAgentData(List<ScenarioResult> scenarios, List<String> usedEndpointIds) {
            this(scenarios, usedEndpointIds, null, null, null);
        }
    }

    public record ScenarioResult(
            @JsonProperty("scenario_id") String scenarioId,
            String name,
            String description,
            String type,
            @JsonProperty("test_level") String testLevel,
            List<ScenarioStepResult> steps,
            JsonNode meta
    ) {
    }

    public record ScenarioStepResult(
            @JsonProperty("endpoint_id") String endpointId,
            String name,
            JsonNode payload,
            JsonNode params,
            @JsonProperty("expected_status_code") Integer expectedStatusCode,
            @JsonProperty("expected_assertions") List<String> expectedAssertions,
            JsonNode requestSpec,
            JsonNode expectedSpec,
            JsonNode assertionSpec
    ) {
    }
}
