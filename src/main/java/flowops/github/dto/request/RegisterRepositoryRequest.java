package flowops.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterRepositoryRequest(
        @Schema(description = "owner/repository 형식의 GitHub 저장소 전체 이름", example = "flowops/backend")
        @NotBlank(message = "저장소 전체 이름은 필수입니다.")
        @Size(max = 255, message = "저장소 전체 이름은 255자를 넘을 수 없습니다.")
        String fullName,
        @Schema(description = "브랜치별 Environment를 자동 생성할 앱 ID", example = "1")
        Long appId,
        @Schema(description = "API 명세 파싱 대상으로 선택한 브랜치 목록", example = "[\"main\", \"develop\"]")
        List<@Size(max = 100, message = "브랜치 이름은 100자를 넘을 수 없습니다.") String> selectedBranches
) {
}
