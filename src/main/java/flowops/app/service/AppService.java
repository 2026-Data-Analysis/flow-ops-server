package flowops.app.service;

import flowops.app.domain.entity.App;
import flowops.app.dto.request.CreateAppRequest;
import flowops.app.dto.response.AppDetailResponse;
import flowops.app.repository.AppRepository;
import flowops.environment.service.EnvironmentProvisioningService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

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

    @Transactional
    public AppDetailResponse setMain(Long appId, String title) {
        App app = getApp(appId);
        app.rename(title);
        return AppDetailResponse.from(app);
    }

    @Transactional(readOnly = true)
    public AppDetailResponse getAppDetail(Long appId) {
        return AppDetailResponse.from(getApp(appId));
    }

    @Transactional
    public void deleteApp(Long appId) {
        App app = getApp(appId);
        deleteDependentData(appId);
        appRepository.delete(app);
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

    private void deleteDependentData(Long appId) {
        execute("""
                delete from reports
                where execution_id in (select id from executions where app_id = :appId)
                """, appId);
        execute("""
                delete from test_validation_results
                where execution_step_id in (
                    select log.id
                    from execution_step_logs log
                    join executions e on e.id = log.execution_id
                    where e.app_id = :appId
                )
                """, appId);
        execute("""
                delete from execution_step_logs
                where execution_id in (select id from executions where app_id = :appId)
                """, appId);
        execute("delete from executions where app_id = :appId", appId);
        execute("""
                delete from generated_test_case_drafts
                where generation_id in (select id from test_generations where app_id = :appId)
                """, appId);
        execute("""
                delete from test_generation_api_selections
                where generation_id in (select id from test_generations where app_id = :appId)
                """, appId);
        execute("delete from test_generations where app_id = :appId", appId);
        execute("""
                delete from test_case_versions
                where test_case_id in (select id from test_cases where app_id = :appId)
                """, appId);
        execute("delete from test_cases where app_id = :appId", appId);
        execute("""
                delete from scenario_steps
                where scenario_id in (select id from scenarios where app_id = :appId)
                """, appId);
        execute("delete from scenarios where app_id = :appId", appId);
        execute("""
                delete from trigger_rules
                where environment_id in (select id from environments where app_id = :appId)
                """, appId);
        execute("delete from environments where app_id = :appId", appId);
        execute("""
                delete from api_inventories
                where repository_id in (select id from project_repositories where app_id = :appId)
                """, appId);
        execute("""
                delete from repository_branches
                where repository_id in (select id from project_repositories where app_id = :appId)
                """, appId);
        execute("delete from project_repositories where app_id = :appId", appId);
        execute("delete from api_endpoints where app_id = :appId", appId);
    }

    private void execute(String sql, Long appId) {
        entityManager.createNativeQuery(sql)
                .setParameter("appId", appId)
                .executeUpdate();
    }
}
