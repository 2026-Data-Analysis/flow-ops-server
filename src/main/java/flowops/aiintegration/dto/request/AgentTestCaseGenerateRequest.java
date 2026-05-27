package flowops.aiintegration.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.FailureContextPayload;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import java.util.List;

public record AgentTestCaseGenerateRequest(
        String agent,
        String requestId,
        String requestedBy,
        ProjectPayload project,
        EnvironmentPayload environment,
        MetadataPayload metadata,
        TestGenerationContext generationContext,
        List<AgentApiSpec> apis,
        List<ExistingTestCasePayload> existingTestCases,
        FailureContextPayload failureContext
) {

    public record AgentApiSpec(
            String apiId,
            String method,
            String path,
            String domainTag,
            JsonNode requestSchema,
            JsonNode responseSchema,
            Boolean authRequired,
            Boolean deprecated
    ) {
    }
}
