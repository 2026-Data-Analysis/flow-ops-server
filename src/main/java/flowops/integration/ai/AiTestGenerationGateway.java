package flowops.integration.ai;

import flowops.testgeneration.domain.entity.TestGeneration;
import java.util.List;

public interface AiTestGenerationGateway {

    List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds);
}
