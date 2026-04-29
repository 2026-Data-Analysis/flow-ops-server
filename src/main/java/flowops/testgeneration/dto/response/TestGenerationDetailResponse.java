package flowops.testgeneration.dto.response;

import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationApiSelection;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record TestGenerationDetailResponse(
        @Schema(description = "생성 작업 ID", example = "77")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "생성 상태", example = "COMPLETED")
        TestGenerationStatus status,
        @Schema(description = "생성 요청자", example = "qa.lead@flowops.dev")
        String requestedBy,
        @ArraySchema(schema = @Schema(description = "선택된 API ID", example = "10"))
        List<Long> selectedApiIds,
        @Schema(description = "생성 컨텍스트 요약", example = "결제 승인과 취소 API 중심으로 회귀 테스트 초안을 생성")
        String contextSummary,
        @Schema(description = "현재 커버리지", example = "42.5")
        Double currentCoverage,
        @Schema(description = "예상 커버리지", example = "52.5")
        Double predictedCoverage,
        @Schema(description = "기존 테스트 수", example = "2")
        Integer existingCount,
        @Schema(description = "신규 테스트 수", example = "4")
        Integer newCount,
        @Schema(description = "중복 테스트 수", example = "1")
        Integer duplicateCount,
        @Schema(description = "생성 일시", example = "2026-04-12T02:00:00")
        LocalDateTime createdAt,
        @Schema(description = "완료 일시", example = "2026-04-12T02:01:00")
        LocalDateTime completedAt
) {

    public static TestGenerationDetailResponse of(
            TestGeneration generation,
            List<TestGenerationApiSelection> selections
    ) {
        return new TestGenerationDetailResponse(
                generation.getId(),
                generation.getApp().getId(),
                generation.getEnvironment() == null ? null : generation.getEnvironment().getId(),
                generation.getStatus(),
                generation.getRequestedBy(),
                selections.stream().map(selection -> selection.getApiEndpoint().getId()).toList(),
                generation.getContextSummary(),
                generation.getCurrentCoverage() == null ? null : generation.getCurrentCoverage().doubleValue(),
                generation.getPredictedCoverage() == null ? null : generation.getPredictedCoverage().doubleValue(),
                generation.getExistingCount(),
                generation.getNewCount(),
                generation.getDuplicateCount(),
                generation.getCreatedAt(),
                generation.getCompletedAt()
        );
    }
}
