package flowops.aiintegration.service;

import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.dto.request.AgentTestCaseGenerateRequest;
import flowops.aiintegration.dto.request.AgentTestCaseGenerateRequest.AgentApiSpec;
import flowops.integration.ai.AiAgentContracts.ExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentTestCaseGenerateService {

    private final AiClient aiClient;

    public TestCaseGeneratorResponse generate(AgentTestCaseGenerateRequest request) {
        validateRequest(request);

        TestCaseGeneratorRequest aiRequest = toAiRequest(request);
        return aiClient.generateTestCaseDrafts(aiRequest);
    }

    private void validateRequest(AgentTestCaseGenerateRequest request) {
        if (request.apis() == null || request.apis().isEmpty()) {
            throw new AgentBadRequestException("No APIs provided for test case generation.");
        }
        if (request.generationContext() != null
                && "FROM_FAILURE".equals(request.generationContext().mode())) {
            throw new AgentBadRequestException("FROM_FAILURE mode is not yet supported.");
        }
    }

    private TestCaseGeneratorRequest toAiRequest(AgentTestCaseGenerateRequest request) {
        List<TestCaseApiPayload> apis = request.apis().stream()
                .map(this::toApiPayload)
                .toList();
        List<TestCaseApiPayload> domainApis = request.domainApis() == null ? List.of() : request.domainApis().stream()
                .map(this::toApiPayload)
                .toList();

        List<ExistingTestCasePayload> existingTestCases =
                request.existingTestCases() == null ? List.of() : request.existingTestCases();

        return new TestCaseGeneratorRequest(
                request.agent(),
                request.requestId(),
                request.requestedBy(),
                request.project(),
                request.environment(),
                request.metadata(),
                request.generationContext(),
                apis,
                domainApis,
                existingTestCases,
                request.failureContext()
        );
    }

    private TestCaseApiPayload toApiPayload(AgentApiSpec api) {
        return new TestCaseApiPayload(
                api.apiId(),
                api.method(),
                api.path(),
                api.domainTag(),
                api.requestSchema(),
                api.responseSchema(),
                api.expectedStatusCodes(),
                api.errorStatusCodes(),
                api.errorCodes(),
                api.authRequired(),
                api.deprecated()
        );
    }

    public static class AgentBadRequestException extends RuntimeException {
        public AgentBadRequestException(String message) {
            super(message);
        }
    }
}
