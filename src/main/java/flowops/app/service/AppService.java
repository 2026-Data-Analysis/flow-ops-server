package flowops.app.service;

import flowops.app.domain.entity.App;
import flowops.app.dto.request.CreateAppRequest;
import flowops.app.dto.response.AppDetailResponse;
import flowops.app.repository.AppRepository;
import flowops.environment.service.EnvironmentProvisioningService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 대상 애플리케이션의 등록과 상세 조회를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class AppService {

    private final AppRepository appRepository;
    private final EnvironmentProvisioningService environmentProvisioningService;

    @Transactional
    public AppDetailResponse createApp(CreateAppRequest request) {
        App app = appRepository.save(App.builder()
                .name(request.name())
                .repoUrl(request.repoUrl())
                .specSource(request.specSource())
                .defaultBranch(request.defaultBranch())
                .build());
        branchNames(request.branches(), request.defaultBranch())
                .forEach(branch -> environmentProvisioningService.ensureBranchEnvironment(
                        app,
                        null,
                        branch,
                        branch.equals(request.defaultBranch())
                ));
        return AppDetailResponse.from(app);
    }

    @Transactional(readOnly = true)
    public AppDetailResponse getAppDetail(Long appId) {
        return AppDetailResponse.from(getApp(appId));
    }

    @Transactional(readOnly = true)
    public App getApp(Long appId) {
        return appRepository.findById(appId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "앱을 찾을 수 없습니다."));
    }

    private Set<String> branchNames(java.util.List<String> requestedBranches, String defaultBranch) {
        Set<String> branches = new LinkedHashSet<>();
        if (requestedBranches != null) {
            requestedBranches.stream()
                    .filter(branch -> branch != null && !branch.isBlank())
                    .map(String::trim)
                    .forEach(branches::add);
        }
        if (branches.isEmpty() && defaultBranch != null && !defaultBranch.isBlank()) {
            branches.add(defaultBranch);
        }
        return branches;
    }
}
