package flowops.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateRepositoryAutoSyncRequest(
        @Schema(description = "Whether Merge push webhooks automatically refresh API Inventory", example = "true")
        @NotNull(message = "autoSyncEnabled is required.")
        Boolean autoSyncEnabled
) {
}
