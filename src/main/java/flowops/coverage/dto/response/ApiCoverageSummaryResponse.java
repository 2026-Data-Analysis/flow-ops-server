package flowops.coverage.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ApiCoverageSummaryResponse(
        @Schema(description = "API ID", example = "10")
        Long apiId,
        @Schema(description = "전체 테스트 케이스 수", example = "12")
        long totalTestCases,
        @Schema(description = "커버리지 비율", example = "60.0")
        double coveragePercent,
        @Schema(description = "갱신 시각", example = "2026-04-12T03:20:00")
        LocalDateTime updatedAt
) {
}
