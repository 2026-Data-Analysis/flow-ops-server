package flowops.execution.dto.response;

import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record GenerateFailureTestCasesResponse(
        @Schema(description = "테스트 생성 작업 ID", example = "77")
        Long generationId,
        @Schema(description = "실행 ID", example = "700")
        Long executionId,
        @Schema(description = "실패 로그 ID", example = "8001")
        Long failedLogId,
        @Schema(description = "API ID", example = "10")
        Long apiId,
        @Schema(description = "오류 메시지", example = "Mock execution failure for placeholder engine.")
        String errorMessage,
        @Schema(description = "기대 동작", example = "응답 상태는 200이어야 합니다.")
        String expectedBehavior,
        @Schema(description = "실제 동작", example = "실제 응답은 500 에러와 오류 본문을 반환했습니다.")
        String actualBehavior,
        @Schema(description = "생성된 실패 기반 테스트 초안")
        List<GeneratedTestCaseDraftResponse> drafts
) {
}
