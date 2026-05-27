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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTestCaseGenerateService {

    private static final int MAX_RETRIES = 2;

    private final AiClient aiClient;

    public TestCaseGeneratorResponse generate(AgentTestCaseGenerateRequest request) {
        validateRequest(request);

        TestCaseGeneratorRequest aiRequest = toAiRequest(request);

        TestCaseGeneratorResponse response = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            response = aiClient.generateTestCaseDrafts(aiRequest);
            if (!isEmpty(response)) {
                return response;
            }
            if (attempt < MAX_RETRIES) {
                log.warn("LLM returned empty drafts, retrying. requestId={}, attempt={}",
                        request.requestId(), attempt + 1);
            }
        }
        log.warn("LLM failed to generate test cases after {} retries. requestId={}",
                MAX_RETRIES, request.requestId());
        throw new AgentBadRequestException("LLM이 2번 재시도 후에도 테스트 케이스를 생성하지 못했습니다.");
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

    private boolean isEmpty(TestCaseGeneratorResponse response) {
        return response == null
                || response.drafts() == null
                || response.drafts().isEmpty();
    }

    private TestCaseGeneratorRequest toAiRequest(AgentTestCaseGenerateRequest request) {
        List<TestCaseApiPayload> apis = request.apis().stream()
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
                existingTestCases,
                request.failureContext()
        );
    }

    private TestCaseApiPayload toApiPayload(AgentApiSpec api) {
        return new TestCaseApiPayload(
                api.apiId(),
                api.apiId(),
                api.method(),
                api.path(),
                api.domainTag(),
                api.requestSchema(),
                api.responseSchema(),
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
