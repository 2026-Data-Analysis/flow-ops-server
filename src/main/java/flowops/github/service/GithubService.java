package flowops.github.service;

import flowops.apiinventory.dto.response.ScanResultResponse;
import flowops.apiinventory.service.ApiInventoryImportService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.service.EnvironmentProvisioningService;
import flowops.github.client.GithubClient;
import flowops.github.domain.entity.RepositoryBranch;
import flowops.github.domain.entity.RepositoryConnectionStatus;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.domain.entity.RepositoryProvider;
import flowops.github.dto.request.RegisterRepositoryRequest;
import flowops.github.dto.request.ScanRepositoryRequest;
import flowops.github.dto.response.BranchResponse;
import flowops.github.dto.response.RepositoryResponse;
import flowops.github.dto.response.RepositorySnapshot;
import flowops.github.repository.RepositoryInfoRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.project.domain.entity.Project;
import flowops.project.service.ProjectService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트에 GitHub 저장소를 연결하고, 선택한 브랜치의 명세를 API Inventory로 가져옵니다.
 */
@Service
@RequiredArgsConstructor
public class GithubService {

    private final ProjectService projectService;
    private final GithubClient githubClient;
    private final RepositoryInfoRepository repositoryInfoRepository;
    private final ApiInventoryImportService apiInventoryImportService;
    private final AppService appService;
    private final EnvironmentProvisioningService environmentProvisioningService;

    @Transactional
    public RepositoryResponse registerRepository(Long projectId, RegisterRepositoryRequest request) {
        Project project = projectService.getProject(projectId);
        RepositoryName repositoryName = RepositoryName.from(request.fullName());
        repositoryInfoRepository.findByFullName(repositoryName.fullName())
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된 저장소입니다.");
                });

        RepositorySnapshot snapshot = githubClient.fetchRepository(repositoryName.owner(), repositoryName.name());
        String defaultBranch = snapshot.defaultBranch();
        Set<String> selectedBranchNames = selectedBranchNames(request.selectedBranches(), defaultBranch);
        List<BranchResponse> branches = githubClient.fetchBranches(repositoryName.owner(), repositoryName.name(), defaultBranch)
                .stream()
                .map(branch -> branch.withSelected(selectedBranchNames.contains(branch.name())))
                .toList();

        RepositoryInfo repositoryInfo = RepositoryInfo.builder()
                .project(project)
                .provider(RepositoryProvider.GITHUB)
                .repositoryName(repositoryName.name())
                .fullName(snapshot.fullName())
                .repositoryUrl(snapshot.htmlUrl())
                .defaultBranch(defaultBranch)
                .externalRepositoryId(snapshot.externalId())
                .connectionStatus(RepositoryConnectionStatus.ACTIVE)
                .lastSyncedAt(LocalDateTime.now())
                .build();

        branches.forEach(branch -> repositoryInfo.addBranch(branch.name(), branch.selected(), branch.isDefault()));
        RepositoryInfo savedRepositoryInfo = repositoryInfoRepository.save(repositoryInfo);

        if (request.appId() != null) {
            App app = appService.getApp(request.appId());
            branches.forEach(branch -> environmentProvisioningService.ensureBranchEnvironment(
                    app,
                    savedRepositoryInfo,
                    branch.name(),
                    branch.isDefault()
            ));
        }

        return RepositoryResponse.from(savedRepositoryInfo, branches, List.of());
    }

    @Transactional(readOnly = true)
    public List<RepositoryResponse> listRepositories(Long projectId) {
        projectService.getProject(projectId);
        return repositoryInfoRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RepositoryResponse getRepositoryDetail(Long projectId, Long repositoryId) {
        RepositoryInfo repositoryInfo = getRepository(repositoryId);
        validateProjectScope(projectId, repositoryInfo);
        return toResponse(repositoryInfo);
    }

    @Transactional(readOnly = true)
    public List<BranchResponse> listRepositoryBranches(Long projectId, Long repositoryId) {
        RepositoryInfo repositoryInfo = getRepository(repositoryId);
        validateProjectScope(projectId, repositoryInfo);
        return repositoryInfo.getBranches().stream()
                .map(this::toBranchResponse)
                .toList();
    }

    @Transactional
    public List<ScanResultResponse> scanRepository(Long projectId, Long repositoryId, ScanRepositoryRequest request) {
        RepositoryInfo repositoryInfo = getRepository(repositoryId);
        validateProjectScope(projectId, repositoryInfo);
        return scanBranchNames(repositoryInfo, request == null ? null : request.branchNames()).stream()
                .map(branchName -> apiInventoryImportService.importFromRepositoryBranch(repositoryInfo, branchName))
                .toList();
    }

    @Transactional(readOnly = true)
    public RepositoryInfo getRepository(Long repositoryId) {
        return repositoryInfoRepository.findById(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "저장소를 찾을 수 없습니다."));
    }

    private RepositoryResponse toResponse(RepositoryInfo repositoryInfo) {
        List<BranchResponse> branches = repositoryInfo.getBranches().stream()
                .map(this::toBranchResponse)
                .toList();
        return RepositoryResponse.from(repositoryInfo, branches, List.of());
    }

    private BranchResponse toBranchResponse(RepositoryBranch branch) {
        return new BranchResponse(
                branch.getBranchName(),
                branch.isDefaultBranch(),
                branch.isSelected()
        );
    }

    private void validateProjectScope(Long projectId, RepositoryInfo repositoryInfo) {
        if (!repositoryInfo.getProject().getId().equals(projectId)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "저장소가 요청한 프로젝트에 속하지 않습니다.");
        }
    }

    private Set<String> selectedBranchNames(List<String> requestedBranches, String defaultBranch) {
        Set<String> selectedBranches = new LinkedHashSet<>();
        if (requestedBranches != null) {
            requestedBranches.stream()
                    .filter(branch -> branch != null && !branch.isBlank())
                    .map(String::trim)
                    .forEach(selectedBranches::add);
        }
        if (selectedBranches.isEmpty()) {
            selectedBranches.add(defaultBranch);
        }
        return selectedBranches;
    }

    private Set<String> scanBranchNames(RepositoryInfo repositoryInfo, List<String> requestedBranches) {
        Set<String> branchNames = new LinkedHashSet<>();
        if (requestedBranches != null) {
            requestedBranches.stream()
                    .filter(branch -> branch != null && !branch.isBlank())
                    .map(String::trim)
                    .forEach(branchNames::add);
        }
        if (!branchNames.isEmpty()) {
            return branchNames;
        }

        repositoryInfo.getBranches().stream()
                .filter(RepositoryBranch::isSelected)
                .map(RepositoryBranch::getBranchName)
                .forEach(branchNames::add);
        if (branchNames.isEmpty()) {
            branchNames.add(repositoryInfo.getDefaultBranch());
        }
        return branchNames;
    }

    private record RepositoryName(String owner, String name) {

        private static RepositoryName from(String fullName) {
            String normalized = fullName == null ? "" : fullName.trim();
            String[] parts = normalized.split("/", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "저장소 전체 이름은 owner/repository 형식이어야 합니다.");
            }
            return new RepositoryName(parts[0], parts[1]);
        }

        private String fullName() {
            return owner + "/" + name;
        }
    }
}
