package flowops.testgeneration.dto.response;

import flowops.execution.support.ExecutionRequestSpecSupport;
import flowops.execution.support.ResponseMetadataSupport;
import flowops.execution.support.ResponseMetadataSupport.ResponseMetadata;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record GeneratedTestCaseDraftResponse(
        @Schema(description = "생성 초안 ID", example = "1001")
        Long id,
        @Schema(description = "생성 작업 ID", example = "77")
        Long generationId,
        @Schema(description = "연결된 API ID", example = "10")
        Long apiId,
        @Schema(description = "AI가 제안한 테스트케이스 이름", example = "주문 생성 성공 케이스")
        String title,
        @Schema(description = "실제로 실행할 HTTP 메서드", example = "POST")
        String executionMethod,
        @Schema(description = "실제로 실행할 엔드포인트", example = "/orders")
        String executionEndpoint,
        @Schema(description = "AI가 제안한 테스트케이스 설명")
        String description,
        @Schema(description = "테스트케이스 유형", example = "HAPPY_PATH")
        String type,
        @Schema(description = "사용자 역할", example = "CUSTOMER")
        String userRole,
        @Schema(description = "사전 상태 조건", example = "로그인된 사용자")
        String stateCondition,
        @Schema(description = "데이터 변형 조건", example = "일반 주문")
        String dataVariant,
        @Schema(description = "요청 명세", example = "{\"body\":{\"productId\":1,\"quantity\":1}}")
        String requestSpec,
        @Schema(description = "기대 결과 명세", example = "{\"status\":201}")
        String expectedSpec,
        @Schema(description = "검증 규칙 명세", example = "{\"assertions\":[\"status == 201\"]}")
        String assertionSpec,
        @Schema(description = "성공 응답으로 기대할 수 있는 HTTP 상태 코드 목록", example = "[200,201]")
        List<Integer> expectedStatusCodes,
        @Schema(description = "오류 응답으로 발생할 수 있는 HTTP 상태 코드 목록", example = "[400,401,404,409,500]")
        List<Integer> errorStatusCodes,
        @Schema(description = "응답 예시에서 추출한 오류 코드 목록", example = "[\"COMMON-400\"]")
        List<String> errorCodes,
        @Schema(description = "중복 후보 여부", example = "false")
        boolean duplicate,
        @Schema(description = "테스트케이스 저장 선택 여부", example = "false")
        boolean selectedForSave,
        @Schema(description = "초안 생성 일시", example = "2026-04-12T02:10:00")
        LocalDateTime createdAt
) {

    public static GeneratedTestCaseDraftResponse from(GeneratedTestCaseDraft draft) {
        ResponseMetadata metadata = ResponseMetadataSupport.from(responseSchema(draft), draft.getExpectedSpec());
        return new GeneratedTestCaseDraftResponse(
                draft.getId(),
                draft.getGeneration().getId(),
                draft.getApiInventory() == null ? draft.getApiEndpoint().getId() : draft.getApiInventory().getId(),
                draft.getTitle(),
                executionMethod(draft),
                executionEndpoint(draft),
                draft.getDescription(),
                draft.getType(),
                draft.getUserRole(),
                draft.getStateCondition(),
                draft.getDataVariant(),
                draft.getRequestSpec(),
                draft.getExpectedSpec(),
                draft.getAssertionSpec(),
                metadata.expectedStatusCodes(),
                metadata.errorStatusCodes(),
                metadata.errorCodes(),
                draft.isDuplicate(),
                draft.isSelectedForSave(),
                draft.getCreatedAt()
        );
    }

    private static String executionMethod(GeneratedTestCaseDraft draft) {
        String override = ExecutionRequestSpecSupport.executionMethod(draft.getRequestSpec());
        return override == null ? draft.getApiEndpoint().getMethod().name() : override;
    }

    private static String executionEndpoint(GeneratedTestCaseDraft draft) {
        String override = ExecutionRequestSpecSupport.executionEndpoint(draft.getRequestSpec());
        return override == null ? draft.getApiEndpoint().getPath() : override;
    }

    private static String responseSchema(GeneratedTestCaseDraft draft) {
        if (draft.getApiInventory() != null) {
            return draft.getApiInventory().getResponseSchema();
        }
        return draft.getApiEndpoint().getResponseSchema();
    }
}
