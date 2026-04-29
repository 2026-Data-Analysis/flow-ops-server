package flowops.environment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConnectionTestResponse(
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "브랜치명", example = "main")
        String branchName,
        @Schema(description = "테스트 상태", example = "SUCCESS")
        String status,
        @Schema(description = "성공한 probe 경로", example = "/actuator/health")
        String successPath,
        @Schema(description = "HTTP 상태 코드", example = "200")
        Integer statusCode,
        @Schema(description = "연결 테스트 메시지", example = "200 응답을 확인했습니다.")
        String message
) {
}
