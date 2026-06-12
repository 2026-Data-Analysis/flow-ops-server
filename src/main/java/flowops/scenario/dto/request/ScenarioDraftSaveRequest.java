package flowops.scenario.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ScenarioDraftSaveRequest(
        Long projectId,
        Long appId,
        String name,
        String description,
        String type,
        String testLevel,
        List<ScenarioStepDraftRequest> steps,
        JsonNode meta
) {
}
