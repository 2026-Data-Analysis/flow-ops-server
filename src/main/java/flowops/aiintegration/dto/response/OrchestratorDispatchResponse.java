package flowops.aiintegration.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
}
