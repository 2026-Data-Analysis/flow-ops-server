package flowops.github.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record RepositorySnapshot(
        @Schema(description = "외부 저장소 ID", example = "123456789")
        String externalId,
        @Schema(description = "전체 저장소 이름", example = "flowops/backend")
        String fullName,
        @Schema(description = "HTML URL", example = "https://github.com/flowops/backend")
        String htmlUrl,
        @Schema(description = "기본 브랜치", example = "main")
        String defaultBranch
) {
}
