package flowops.apiinventory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiInventoryBranchSummaryResponse(
        @Schema(description = "브랜치명", example = "main")
        String branchName,
        @Schema(description = "브랜치별 총 API 수", example = "42")
        long totalApiCount,
        @Schema(description = "브랜치별 평균 커버리지", example = "63.5")
        double averageCoverage,
        @Schema(description = "브랜치별 전체 테스트 수", example = "128")
        long totalTestCount
) {
}
