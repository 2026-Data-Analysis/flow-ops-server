package flowops.scenario.dto.response;

import flowops.scenario.domain.entity.ScenarioType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScenarioRecommendationResponse(
        @Schema(description = "Recommended scenario name", example = "Critical checkout flow")
        String name,
        @Schema(description = "Recommended scenario type", example = "HAPPY_PATH")
        ScenarioType type,
        @Schema(description = "Recommendation reason", example = "Covers a high-value multi-endpoint business path.")
        String recommendationReason,
        @Schema(description = "AI-recommended scenario steps")
        List<Step> steps
) {

    public record Step(
            @Schema(description = "Step order", example = "1")
            Integer stepOrder,
            @Schema(description = "FlowOps API ID. Use this as apiId when creating the scenario.", example = "10")
            Long apiId,
            @Schema(description = "AI response endpoint_id", example = "POST:/payments")
            String endpointId,
            @Schema(description = "Step label", example = "Request payment approval")
            String label,
            @Schema(description = "Step draft type", example = "HAPPY_PATH")
            String type,
            @Schema(description = "Step description")
            String description,
            @Schema(description = "Test level inherited from the scenario", example = "REGRESSION")
            String testLevel,
            @Schema(description = "User role context")
            String userRole,
            @Schema(description = "Precondition state")
            String stateCondition,
            @Schema(description = "Data variant")
            String dataVariant,
            @Schema(description = "Request config JSON")
            String requestConfig,
            @Schema(description = "Extraction rules JSON")
            String extractRules,
            @Schema(description = "Validation rules JSON")
            String validationRules
    ) {
    }
}
