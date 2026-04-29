package flowops.apiinventory.dto.response;

import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;

public record SampleTestCaseResponse(
        @Schema(description = "테스트 케이스 ID", example = "501")
        Long id,
        @Schema(description = "테스트 케이스 이름", example = "주문 상세 조회 성공")
        String name,
        @Schema(description = "테스트 유형", example = "HAPPY_PATH")
        TestCaseType type,
        @Schema(description = "테스트 위계", example = "SMOKE")
        TestLevel testLevel,
        @Schema(description = "생성 상태", example = "AUTO")
        TestCaseSource source
) {
    public static SampleTestCaseResponse from(TestCase testCase) {
        return new SampleTestCaseResponse(
                testCase.getId(),
                testCase.getName(),
                testCase.getType(),
                testCase.getTestLevel(),
                testCase.getSource()
        );
    }
}
