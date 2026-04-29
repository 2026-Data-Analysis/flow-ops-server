package flowops.apiinventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiInventoryListResponse(
        @Schema(description = "기본 조회 브랜치명", example = "main")
        String defaultBranchName,
        @Schema(description = "브랜치별 요약")
        List<ApiInventoryBranchSummaryResponse> branchSummaries,
        @Schema(description = "API 인벤토리 목록")
        List<ApiInventoryResponse> items
) {
}
