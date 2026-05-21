package flowops.integration.ai;

import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.environment.domain.entity.Environment;
import flowops.testgeneration.domain.entity.TestGeneration;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.ai", name = "mock-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class MockAiTestGenerationGateway implements AiTestGenerationGateway {

    @PostConstruct
    void logMockGatewayEnabled() {
        log.warn("Mock AI test generation gateway is enabled because external.ai.mock-enabled=true or missing");
    }

    @Override
    public List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds, Environment sourceEnvironment) {
        log.warn("Returning mock AI test drafts. generationId={}, appId={}, sourceEnvironmentId={}, apiCount={}",
                generation.getId(),
                generation.getApp() == null ? null : generation.getApp().getId(),
                sourceEnvironment == null ? null : sourceEnvironment.getId(),
                apiIds == null ? 0 : apiIds.size());
        List<AiGeneratedDraftCommand> drafts = new ArrayList<>();
        for (Long apiId : apiIds) {
            drafts.add(new AiGeneratedDraftCommand(
                    apiId,
                    "Generated happy path for API " + apiId,
                    "Mocked AI-generated draft based on the current API metadata.",
                    "HAPPY_PATH",
                    "QA_ENGINEER",
                    "Seed data available",
                    "baseline",
                    "{\"body\":\"sample request\"}",
                    "{\"status\":200}",
                    "{\"assertions\":[\"status == 200\"]}",
                    false
            ));
            drafts.add(new AiGeneratedDraftCommand(
                    apiId,
                    "Generated validation case for API " + apiId,
                    "Mocked duplicate comparison draft for validation behavior.",
                    "VALIDATION",
                    "QA_ENGINEER",
                    "Missing required field",
                    "invalid-input",
                    "{\"body\":\"invalid request\"}",
                    "{\"status\":400}",
                    "{\"assertions\":[\"status == 400\"]}",
                    true
            ));
        }
        return drafts;
    }

    @Override
    public List<AiGeneratedDraftCommand> generateDraftsFromFailure(TestGeneration generation, Execution execution, ExecutionStepLog failedLog) {
        log.warn("Returning mock AI failure drafts. generationId={}, executionId={}, failedLogId={}",
                generation.getId(),
                execution.getId(),
                failedLog.getId());
        Long apiId = failedLog.getTestCase() != null
                ? failedLog.getTestCase().getApiEndpoint().getId()
                : failedLog.getScenarioStep() != null
                ? failedLog.getScenarioStep().getApiEndpoint().getId()
                : execution.getTargetId();
        return List.of(
                new AiGeneratedDraftCommand(
                        apiId,
                        "Failure reproduction for API " + apiId,
                        "Mocked failure-based draft using the failed execution log.",
                        "FAILURE_HANDLING",
                        "QA_ENGINEER",
                        "Same environment and data as the failed execution",
                        "failure-reproduction",
                        failedLog.getRequestBody(),
                        "{\"status\":200}",
                        "{\"assertions\":[\"status == 200\",\"previous failure should not recur\"]}",
                        false
                ),
                new AiGeneratedDraftCommand(
                        apiId,
                        "Regression guard for API " + apiId,
                        "Mocked regression draft to prevent the same failure.",
                        "VALIDATION",
                        "QA_ENGINEER",
                        "After the failure is fixed",
                        "post-fix-regression",
                        failedLog.getRequestBody(),
                        "{\"status\":200}",
                        "{\"assertions\":[\"status == 200\"]}",
                        false
                )
        );
    }
}
