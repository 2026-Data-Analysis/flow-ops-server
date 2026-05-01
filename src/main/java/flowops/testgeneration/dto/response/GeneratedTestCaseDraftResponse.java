package flowops.testgeneration.dto.response;

import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record GeneratedTestCaseDraftResponse(
        @Schema(description = "생성 초안 ID", example = "1001")
        Long id,
        @Schema(description = "생성 작업 ID", example = "77")
        Long generationId,
        @Schema(description = "연결된 API ID", example = "10")
        Long apiId,
        @Schema(description = "AI가 제안한 테스트 케이스 이름", example = "Generated happy path for API 10")
        String title,
        @Schema(description = "AI가 제안한 테스트 케이스 설명", example = "Mocked AI-generated draft based on the current API metadata.")
        String description,
        @Schema(description = "테스트 케이스 유형", example = "HAPPY_PATH")
        String type,
        @Schema(description = "사용자 역할", example = "QA_ENGINEER")
        String userRole,
        @Schema(description = "사전 상태 조건", example = "Seed data available")
        String stateCondition,
        @Schema(description = "데이터 변형 조건", example = "baseline")
        String dataVariant,
        @Schema(description = "요청 명세", example = "{\"body\":\"sample request\"}")
        String requestSpec,
        @Schema(description = "기대 결과 명세", example = "{\"status\":200}")
        String expectedSpec,
        @Schema(description = "검증 규칙 명세", example = "{\"assertions\":[\"status == 200\"]}")
        String assertionSpec,
        @Schema(description = "중복 후보 여부", example = "false")
        boolean duplicate,
        @Schema(description = "테스트 케이스로 저장 선택 여부", example = "false")
        boolean selectedForSave,
        @Schema(description = "초안 생성 일시", example = "2026-04-12T02:10:00")
        LocalDateTime createdAt
) {

    public static GeneratedTestCaseDraftResponse from(GeneratedTestCaseDraft draft) {
        return new GeneratedTestCaseDraftResponse(
                draft.getId(),
                draft.getGeneration().getId(),
                draft.getApiEndpoint().getId(),
                draft.getTitle(),
                draft.getDescription(),
                draft.getType(),
                draft.getUserRole(),
                draft.getStateCondition(),
                draft.getDataVariant(),
                draft.getRequestSpec(),
                draft.getExpectedSpec(),
                draft.getAssertionSpec(),
                draft.isDuplicate(),
                draft.isSelectedForSave(),
                draft.getCreatedAt()
        );
    }
}
