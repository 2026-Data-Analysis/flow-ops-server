package flowops.project.service;

import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.project.domain.entity.Project;
import flowops.project.domain.entity.ProjectStatus;
import flowops.project.dto.request.CreateProjectRequest;
import flowops.project.dto.response.ProjectResponse;
import flowops.project.repository.ProjectRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트 생성과 조회의 기준점을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        Project project = projectRepository.save(Project.builder()
                .name(request.name())
                .slug(generateUniqueSlug(request.name()))
                .description(request.description())
                .status(ProjectStatus.ACTIVE)
                .build());

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public Project getProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (baseSlug.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "프로젝트 이름을 유효한 슬러그로 변환할 수 없습니다.");
        }

        String candidate = baseSlug;
        int sequence = 1;
        while (projectRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + sequence++;
        }
        return candidate;
    }
}
