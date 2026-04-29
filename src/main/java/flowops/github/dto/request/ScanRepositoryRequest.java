package flowops.github.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ScanRepositoryRequest(
        @Schema(description = "스캔할 브랜치 목록. 비어 있으면 저장소 등록 시 선택한 브랜치를 스캔합니다.", example = "[\"main\", \"develop\"]")
        List<@Size(max = 100, message = "브랜치 이름은 100자를 넘을 수 없습니다.") String> branchNames
) {
}
