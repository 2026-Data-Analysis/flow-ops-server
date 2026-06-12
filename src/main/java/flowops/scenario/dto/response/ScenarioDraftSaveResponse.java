package flowops.scenario.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ScenarioDraftSaveResponse(
        @JsonProperty("scenario_id")
        Long scenarioId,
        String name,
        @JsonProperty("step_count")
        int stepCount,
        boolean saved
) {
}
