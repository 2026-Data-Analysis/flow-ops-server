package flowops.coverage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ExecutionCoverageImpactResponse(
        @Schema(description = "실행 ID", example = "700")
        Long executionId,
        @Schema(description = "실행 전 커버리지", example = "35.0")
        double beforeCoverage,
        @Schema(description = "실행 후 커버리지", example = "42.5")
        double afterCoverage,
        @Schema(description = "증감치", example = "7.5")
        double delta
) {
}
