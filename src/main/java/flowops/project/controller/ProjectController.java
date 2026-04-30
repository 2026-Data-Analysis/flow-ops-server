package flowops.project.controller;

import flowops.global.response.ApiResponse;
import flowops.project.dto.request.CreateProjectRequest;
import flowops.project.dto.response.ProjectResponse;
import flowops.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 생성 API를 노출합니다.
 */
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
@Tag(name = "Legacy Project", description = "기존 프로젝트 생성 API")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "프로젝트 목록 조회", description = "등록된 프로젝트 목록을 조회합니다.")
    public ApiResponse<List<ProjectResponse>> listProjects() {
        return ApiResponse.success(projectService.listProjects());
    }

    @PostMapping
    @Operation(summary = "프로젝트 생성", description = "기존 프로젝트 리소스를 생성합니다.")
    public ApiResponse<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.success(projectService.createProject(request));
    }
}
