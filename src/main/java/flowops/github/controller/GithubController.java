package flowops.github.controller;

import flowops.apiinventory.dto.response.ScanResultResponse;
import flowops.global.response.ApiResponse;
import flowops.github.dto.request.RegisterRepositoryRequest;
import flowops.github.dto.request.ScanRepositoryRequest;
import flowops.github.dto.response.RepositoryResponse;
import flowops.github.service.GithubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트와 GitHub 저장소 연결/조회/스캔 API를 제공합니다.
 */
@RestController
@RequestMapping("/projects/{projectId}/repositories")
@RequiredArgsConstructor
@Tag(name = "Repository", description = "GitHub 저장소 등록 및 스캔 API")
public class GithubController {

    private final GithubService githubService;

    @PostMapping
    @Operation(summary = "저장소 등록", description = "프로젝트에 GitHub 저장소 연결 정보를 등록합니다.")
    public ApiResponse<RepositoryResponse> registerRepository(
            @PathVariable Long projectId,
            @Valid @RequestBody RegisterRepositoryRequest request
    ) {
        return ApiResponse.success(githubService.registerRepository(projectId, request));
    }

    @GetMapping
    @Operation(summary = "저장소 목록 조회", description = "프로젝트에 등록된 GitHub 저장소 목록과 브랜치 정보를 조회합니다.")
    public ApiResponse<List<RepositoryResponse>> listRepositories(@PathVariable Long projectId) {
        return ApiResponse.success(githubService.listRepositories(projectId));
    }

    @GetMapping("/{repositoryId}")
    @Operation(summary = "저장소 상세 조회", description = "선택한 GitHub 저장소 상세와 브랜치 정보를 조회합니다.")
    public ApiResponse<RepositoryResponse> getRepository(
            @PathVariable Long projectId,
            @PathVariable Long repositoryId
    ) {
        return ApiResponse.success(githubService.getRepositoryDetail(projectId, repositoryId));
    }

    @PostMapping("/{repositoryId}/scan")
    @Operation(summary = "저장소 API 스캔", description = "선택한 GitHub 저장소 브랜치에서 OpenAPI/Swagger 명세를 파싱해 API Inventory에 반영합니다.")
    public ApiResponse<List<ScanResultResponse>> scanRepository(
            @PathVariable Long projectId,
            @PathVariable Long repositoryId,
            @Valid @RequestBody(required = false) ScanRepositoryRequest request
    ) {
        return ApiResponse.success(githubService.scanRepository(projectId, repositoryId, request));
    }
}
