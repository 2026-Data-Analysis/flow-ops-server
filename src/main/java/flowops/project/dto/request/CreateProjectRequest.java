package flowops.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @Schema(description = "프로젝트 이름", example = "FlowOps Core")
        @NotBlank @Size(max = 120) String name,
        @Schema(description = "프로젝트 설명", example = "API QA automation workspace")
        @Size(max = 2000) String description
) {
}
