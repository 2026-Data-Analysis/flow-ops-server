package flowops.app.dto.response;

import flowops.app.domain.entity.App;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AppDetailResponse(
        @Schema(description = "앱 ID", example = "1")
        Long id,
        @Schema(description = "애플리케이션 이름", example = "Payment API")
        String name,
        @Schema(description = "저장소 URL", example = "https://github.com/flowops/payment-api")
        String repoUrl,
        @Schema(description = "스펙 소스", example = "s3://flowops/specs/payment/openapi.yaml")
        String specSource,
        @Schema(description = "기본 브랜치", example = "main")
        String defaultBranch,
        @Schema(description = "생성 일시", example = "2026-04-12T01:00:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T01:30:00")
        LocalDateTime updatedAt
) {

    public static AppDetailResponse from(App app) {
        return new AppDetailResponse(
                app.getId(),
                app.getName(),
                app.getRepoUrl(),
                app.getSpecSource(),
                app.getDefaultBranch(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
