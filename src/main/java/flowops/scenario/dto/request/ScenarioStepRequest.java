package flowops.scenario.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScenarioStepRequest(
        @Schema(description = "Existing scenario step id. Null when creating a new step.", example = "901")
        Long id,

        @Schema(description = "Step order", example = "1")
        @NotNull(message = "Step order is required.")
        Integer stepOrder,

        @Schema(description = "FlowOps API id", example = "10")
        @NotNull(message = "API id is required.")
        Long apiId,

        @Schema(description = "Step label", example = "Request payment approval")
        @NotBlank(message = "Step label is required.")
        String label,

        @Schema(description = "AI-generated step id", example = "step-1")
        String stepId,
        @Schema(description = "Short step reference", example = "step_1")
        String ref,
        @Schema(description = "Chained variables JSON")
        JsonNode chainedVariables,
        @Schema(description = "Scenario step type", example = "HAPPY_PATH")
        String type,
        @Schema(description = "Step test level context", example = "REGRESSION")
        String testLevel,
        @Schema(description = "User role context", example = "CUSTOMER")
        String userRole,
        @Schema(description = "State precondition")
        String stateCondition,
        @Schema(description = "Data variant")
        String dataVariant,
        @Schema(description = "Executable request spec JSON")
        JsonNode requestSpec,
        @Schema(description = "Expected response spec JSON")
        JsonNode expectedSpec,
        @Schema(description = "Assertion spec JSON")
        JsonNode assertionSpec,
        @Schema(description = "Whether this step is a duplicate")
        Boolean duplicate,

        @Schema(description = "Legacy execution request config JSON", example = "{\"body\":{\"amount\":10000}}")
        String requestConfig,
        @Schema(description = "Legacy extraction rules JSON", example = "{\"paymentId\":\"$.paymentId\"}")
        String extractRules,
        @Schema(description = "Legacy validation rules JSON", example = "{\"assertions\":[\"status == 200\"]}")
        String validationRules
) {
}
