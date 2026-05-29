package flowops.aiintegration.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record OrchestratorDispatchRequest(
        @JsonProperty("project_id") String projectId,
        @NotBlank @JsonProperty("user_prompt") String userPrompt,
        JsonNode context
) {
}
