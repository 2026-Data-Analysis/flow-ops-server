package flowops.scenario.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScenarioDraftBulkSaveResponse(
        @JsonProperty("saved_count")
        int savedCount,
        List<ScenarioDraftSaveResponse> scenarios
) {
}
