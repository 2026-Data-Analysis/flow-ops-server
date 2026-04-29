package flowops.testcase.dto.response;

import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record TestCaseDetailResponse(
        @Schema(description = "테스트 케이스 ID", example = "501")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "API ID", example = "10")
        Long apiId,
        @Schema(description = "테스트 케이스 이름", example = "결제 승인 정상 흐름")
        String name,
        @Schema(description = "설명", example = "유효한 카드 정보로 결제 승인 요청 시 200 응답을 검증합니다.")
        String description,
        @Schema(description = "테스트 유형", example = "HAPPY_PATH")
        TestCaseType type,
        @Schema(description = "테스트 레벨", example = "REGRESSION")
        TestLevel testLevel,
        @Schema(description = "생성 소스", example = "EDITED")
        TestCaseSource source,
        @Schema(description = "사용자 역할", example = "CUSTOMER")
        String userRole,
        @Schema(description = "상태 조건", example = "로그인된 사용자")
        String stateCondition,
        @Schema(description = "데이터 변형", example = "valid-card")
        String dataVariant,
        @Schema(description = "요청 명세", example = "{\"amount\":10000,\"currency\":\"KRW\"}")
        String requestSpec,
        @Schema(description = "기대 명세", example = "{\"status\":200,\"approved\":true}")
        String expectedSpec,
        @Schema(description = "검증 명세", example = "{\"assertions\":[\"status == 200\",\"approved == true\"]}")
        String assertionSpec,
        @Schema(description = "활성 여부", example = "true")
        boolean active,
        @Schema(description = "현재 버전", example = "3")
        Integer version,
        @Schema(description = "생성 일시", example = "2026-04-10T10:00:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T02:30:00")
        LocalDateTime updatedAt
) {

    public static TestCaseDetailResponse from(TestCase testCase) {
        return new TestCaseDetailResponse(
                testCase.getId(),
                testCase.getApp().getId(),
                testCase.getApiEndpoint().getId(),
                testCase.getName(),
                testCase.getDescription(),
                testCase.getType(),
                testCase.getTestLevel(),
                testCase.getSource(),
                testCase.getUserRole(),
                testCase.getStateCondition(),
                testCase.getDataVariant(),
                testCase.getRequestSpec(),
                testCase.getExpectedSpec(),
                testCase.getAssertionSpec(),
                testCase.isActive(),
                testCase.getVersion(),
                testCase.getCreatedAt(),
                testCase.getUpdatedAt()
        );
    }
}
