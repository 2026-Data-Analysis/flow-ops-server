package flowops.testgeneration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SaveGeneratedDraftsResponse(
        @Schema(description = "Generation job ID", example = "77")
        Long generationId,
        @Schema(description = "Saved test case count", example = "3")
        int savedCount,
        @Schema(description = "Saved test case IDs", example = "[501, 502]")
        List<Long> savedTestCaseIds,
        @Schema(description = "Selected API IDs. Inventory IDs when available, otherwise endpoint PKs.", example = "[2248, 2056]")
        List<Long> apiIds,
        @Schema(description = "API inventory IDs for saved test cases.", example = "[2248]")
        List<Long> apiInventoryIds,
        @Schema(description = "Legacy API endpoint PKs for saved test cases.", example = "[10, 11]")
        List<Long> apiEndpointIds
) {
}
