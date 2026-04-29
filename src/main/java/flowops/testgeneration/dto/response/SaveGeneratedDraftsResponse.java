package flowops.testgeneration.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SaveGeneratedDraftsResponse(
        @Schema(description = "생성 작업 ID", example = "77")
        Long generationId,
        @Schema(description = "저장된 테스트 케이스 수", example = "3")
        int savedCount,
        @Schema(description = "저장된 테스트 케이스 ID 목록", example = "[501, 502]")
        List<Long> savedTestCaseIds,
        @Schema(description = "저장된 테스트 케이스가 연결된 API ID 목록", example = "[10, 11]")
        List<Long> apiIds
) {
}
