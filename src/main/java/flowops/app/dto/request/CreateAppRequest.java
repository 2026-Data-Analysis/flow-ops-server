package flowops.app.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateAppRequest(
        @Schema(description = "애플리케이션 이름", example = "Payment API")
        @NotBlank(message = "앱 이름은 필수입니다.")
        @Size(max = 120, message = "앱 이름은 120자 이하여야 합니다.")
        String name,

        @Schema(description = "소스 저장소 URL", example = "https://github.com/flowops/payment-api")
        @Size(max = 1000, message = "저장소 URL은 1000자 이하여야 합니다.")
        String repoUrl,

        @Schema(description = "OpenAPI 스펙 소스 위치", example = "s3://flowops/specs/payment/openapi.yaml")
        @Size(max = 500, message = "명세 출처는 500자 이하여야 합니다.")
        String specSource,

        @Schema(description = "기본 브랜치명", example = "main")
        @Size(max = 100, message = "기본 브랜치명은 100자 이하여야 합니다.")
        String defaultBranch,

        @Schema(description = "앱 등록 시 자동 Environment를 생성할 브랜치 목록", example = "[\"main\", \"develop\"]")
        List<@Size(max = 100, message = "브랜치명은 100자 이하여야 합니다.") String> branches
) {
}
