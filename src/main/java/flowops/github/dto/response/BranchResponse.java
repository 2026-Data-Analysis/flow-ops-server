package flowops.github.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record BranchResponse(
        @Schema(description = "브랜치 이름", example = "main")
        String name,
        @Schema(description = "기본 브랜치 여부", example = "true")
        boolean isDefault,
        @Schema(description = "API 명세 파싱 대상으로 선택된 브랜치 여부", example = "true")
        boolean selected
) {
    public BranchResponse withSelected(boolean selected) {
        return new BranchResponse(name, isDefault, selected);
    }
}
