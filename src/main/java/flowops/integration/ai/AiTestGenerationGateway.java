package flowops.integration.ai;

import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import java.util.List;

public interface AiTestGenerationGateway {

    List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds);

    List<AiGeneratedDraftCommand> generateDraftsFromFailure(TestGeneration generation, Execution execution, ExecutionStepLog failedLog);
}
