package flowops.environment.dto.request;

import flowops.environment.domain.entity.AuthType;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @Schema(description = "환경 이름", example = "main")
        @NotBlank(message = "환경 이름은 필수입니다.")
        @Size(max = 30, message = "환경 이름은 30자 이하여야 합니다.")
        String name,

        @Schema(description = "연결 브랜치명", example = "main")
        @Size(max = 100, message = "브랜치명은 100자 이하여야 합니다.")
        String branchName,

        @Schema(description = "기본 URL", example = "https://staging-api.flowops.dev")
        @NotBlank(message = "기본 URL은 필수입니다.")
        @Size(max = 1000, message = "기본 URL은 1000자 이하여야 합니다.")
        String baseUrl,

        @Schema(description = "인증 방식", example = "BEARER")
        @NotNull(message = "인증 유형은 필수입니다.")
        AuthType authType,

        @Schema(description = "인증 설정 JSON 또는 토큰 값. 응답에서는 마스킹됩니다.", example = "{\"token\":\"plain-token\"}")
        String authConfig,

        @Schema(description = "기본 헤더 JSON", example = "{\"X-Tenant-Id\":\"flowops\"}")
        String headers,

        @Schema(description = "기본 테스트 위계", example = "REGRESSION")
        @NotNull(message = "기본 테스트 위계는 필수입니다.")
        TestLevel defaultTestLevel,

        @Schema(description = "기본 테스트 위계 결정 출처", example = "MANUAL")
        TestLevelSource defaultTestLevelSource
) {
}
