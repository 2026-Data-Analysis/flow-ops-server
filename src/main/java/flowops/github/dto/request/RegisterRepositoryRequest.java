package flowops.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterRepositoryRequest(
        @Schema(description = "GitHub repository full name in owner/repository format", example = "flowops/backend")
        @NotBlank(message = "Repository fullName is required.")
        @Size(max = 255, message = "Repository fullName must be 255 characters or less.")
        String fullName,
        @Schema(description = "App ID to connect with this repository", example = "1")
        Long appId,
        @Schema(description = "Branches selected for API inventory scans", example = "[\"main\", \"develop\"]")
        List<@Size(max = 100, message = "Branch name must be 100 characters or less.") String> selectedBranches,
        @Schema(description = "Whether Merge push webhooks automatically refresh API Inventory", example = "true")
        Boolean autoSyncEnabled
) {
}
