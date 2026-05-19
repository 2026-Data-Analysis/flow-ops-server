package flowops.github.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import flowops.apiinventory.service.ApiInventoryImportService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.service.EnvironmentProvisioningService;
import flowops.github.client.GithubClient;
import flowops.github.domain.entity.RepositoryConnectionStatus;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.domain.entity.RepositoryProvider;
import flowops.github.dto.request.RegisterRepositoryRequest;
import flowops.github.repository.RepositoryInfoRepository;
import flowops.project.domain.entity.Project;
import flowops.project.domain.entity.ProjectStatus;
import flowops.project.service.ProjectService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GithubServiceTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private GithubClient githubClient;

    @Mock
    private RepositoryInfoRepository repositoryInfoRepository;

    @Mock
    private ApiInventoryImportService apiInventoryImportService;

    @Mock
    private AppService appService;

    @Mock
    private EnvironmentProvisioningService environmentProvisioningService;

    @InjectMocks
    private GithubService githubService;

    @Test
    void provisionsSelectedBranchEnvironmentsWhenExistingRepositoryIsConnectedToApp() {
        App app = app(1L);
        RepositoryInfo repositoryInfo = repositoryInfo();
        repositoryInfo.addBranch("main", true, true);
        repositoryInfo.addBranch("develop", true, false);
        repositoryInfo.addBranch("docs", false, false);
        when(repositoryInfoRepository.findByFullName("flowops/backend")).thenReturn(Optional.of(repositoryInfo));
        when(appService.getApp(1L)).thenReturn(app);

        githubService.registerRepository(1L, new RegisterRepositoryRequest(
                "flowops/backend",
                1L,
                List.of("main", "develop")
        ));

        verify(environmentProvisioningService).ensureBranchEnvironment(app, repositoryInfo, "main", true);
        verify(environmentProvisioningService).ensureBranchEnvironment(app, repositoryInfo, "develop", false);
        verify(environmentProvisioningService, never()).ensureBranchEnvironment(app, repositoryInfo, "docs", false);
    }

    private App app(Long id) {
        App app = App.builder()
                .name("FlowOps")
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private RepositoryInfo repositoryInfo() {
        RepositoryInfo repositoryInfo = RepositoryInfo.builder()
                .project(project())
                .provider(RepositoryProvider.GITHUB)
                .repositoryName("backend")
                .fullName("flowops/backend")
                .repositoryUrl("https://github.com/flowops/backend")
                .defaultBranch("main")
                .connectionStatus(RepositoryConnectionStatus.ACTIVE)
                .lastSyncedAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(repositoryInfo, "id", 10L);
        return repositoryInfo;
    }

    private Project project() {
        Project project = Project.builder()
                .name("Default")
                .slug("default")
                .status(ProjectStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(project, "id", 1L);
        return project;
    }
}
