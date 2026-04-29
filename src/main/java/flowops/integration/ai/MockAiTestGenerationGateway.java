package flowops.integration.ai;

import flowops.testgeneration.domain.entity.TestGeneration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MockAiTestGenerationGateway implements AiTestGenerationGateway {

    @Override
    public List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds) {
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
}
