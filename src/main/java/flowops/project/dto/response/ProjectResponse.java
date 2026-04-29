package flowops.project.dto.response;

import flowops.project.domain.entity.Project;
import flowops.project.domain.entity.ProjectStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectResponse(
        @Schema(description = "프로젝트 ID", example = "1")
        Long id,
        @Schema(description = "프로젝트 이름", example = "FlowOps Core")
        String name,
        @Schema(description = "슬러그", example = "flowops-core")
        String slug,
        @Schema(description = "설명", example = "API QA automation workspace")
        String description,
        @Schema(description = "프로젝트 상태", example = "ACTIVE")
        ProjectStatus status
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getDescription(),
                project.getStatus()
        );
    }
}
