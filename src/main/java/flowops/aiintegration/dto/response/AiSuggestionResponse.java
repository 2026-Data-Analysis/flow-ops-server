package flowops.aiintegration.dto.response;

import flowops.aiintegration.domain.entity.AiSuggestion;
import flowops.aiintegration.domain.entity.AiSuggestionStatus;
import flowops.aiintegration.domain.entity.AiSuggestionType;
import io.swagger.v3.oas.annotations.media.Schema;

public record AiSuggestionResponse(
        @Schema(description = "AI 제안 ID", example = "55")
        Long id,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "제안 타입", example = "SPEC_ANALYSIS")
        AiSuggestionType suggestionType,
        @Schema(description = "처리 상태", example = "COMPLETED")
        AiSuggestionStatus status,
        @Schema(description = "원본 참조", example = "s3://specs/order/openapi.yaml")
        String sourceReference,
        @Schema(description = "출력 텍스트", example = "인증 누락 가능성이 있는 엔드포인트가 있습니다.")
        String outputText,
        @Schema(description = "모델 이름", example = "gpt-5.4")
        String modelName
) {
    public static AiSuggestionResponse from(AiSuggestion aiSuggestion) {
        return new AiSuggestionResponse(
                aiSuggestion.getId(),
                aiSuggestion.getProject().getId(),
                aiSuggestion.getSuggestionType(),
                aiSuggestion.getStatus(),
                aiSuggestion.getSourceReference(),
                aiSuggestion.getOutputText(),
                aiSuggestion.getModelName()
        );
    }
}
