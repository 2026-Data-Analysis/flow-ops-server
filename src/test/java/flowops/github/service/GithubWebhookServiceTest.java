package flowops.github.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.apiinventory.service.ApiInventoryImportService;
import flowops.github.domain.entity.RepositoryConnectionStatus;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.domain.entity.RepositoryProvider;
import flowops.github.repository.RepositoryInfoRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.project.domain.entity.Project;
import flowops.project.domain.entity.ProjectStatus;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GithubWebhookServiceTest {

    private final RepositoryInfoRepository repositoryInfoRepository = mock(RepositoryInfoRepository.class);
    private final ApiInventoryImportService apiInventoryImportService = mock(ApiInventoryImportService.class);
    private final GithubWebhookService service = new GithubWebhookService(
            new ObjectMapper(),
            new ExternalServiceProperties(
                    new ExternalServiceProperties.Github(null, null, null, "main", true, 0, 0, 0, 0),
                    null
            ),
            repositoryInfoRepository,
            apiInventoryImportService
    );

    @Test
    void scansMainBranchWhenAnyPushedCommitMessageStartsWithMerge() {
        RepositoryInfo repositoryInfo = RepositoryInfo.builder()
                .project(Project.builder()
                        .name("FlowOps")
                        .slug("flowops")
                        .status(ProjectStatus.ACTIVE)
                        .build())
                .provider(RepositoryProvider.GITHUB)
                .repositoryName("flowops-server")
                .fullName("2026-Data-Analysis/flow-ops-server")
                .repositoryUrl("https://github.com/2026-Data-Analysis/flow-ops-server")
                .defaultBranch("main")
                .connectionStatus(RepositoryConnectionStatus.ACTIVE)
                .build();
        when(repositoryInfoRepository.findByFullName("2026-Data-Analysis/flow-ops-server"))
                .thenReturn(Optional.of(repositoryInfo));

        service.handle("push", null, """
                {
                  "ref": "refs/heads/main",
                  "repository": {
                    "full_name": "2026-Data-Analysis/flow-ops-server"
                  },
                  "head_commit": {
                    "message": "Update generated files"
                  },
                  "commits": [
                    { "message": "Fix API schema" },
                    { "message": "Merge remote-tracking branch 'origin/main'" }
                  ]
                }
                """);

        verify(apiInventoryImportService).importFromRepositoryBranch(eq(repositoryInfo), eq("main"));
    }

    @Test
    void skipsScanWhenRepositoryAutoSyncIsDisabled() {
        RepositoryInfo repositoryInfo = RepositoryInfo.builder()
                .project(Project.builder()
                        .name("FlowOps")
                        .slug("flowops")
                        .status(ProjectStatus.ACTIVE)
                        .build())
                .provider(RepositoryProvider.GITHUB)
                .repositoryName("flowops-server")
                .fullName("2026-Data-Analysis/flow-ops-server")
                .repositoryUrl("https://github.com/2026-Data-Analysis/flow-ops-server")
                .defaultBranch("main")
                .connectionStatus(RepositoryConnectionStatus.ACTIVE)
                .autoSyncEnabled(false)
                .build();
        when(repositoryInfoRepository.findByFullName("2026-Data-Analysis/flow-ops-server"))
                .thenReturn(Optional.of(repositoryInfo));

        service.handle("push", null, """
                {
                  "ref": "refs/heads/main",
                  "repository": {
                    "full_name": "2026-Data-Analysis/flow-ops-server"
                  },
                  "head_commit": {
                    "message": "Merge pull request #1 from feature/api"
                  },
                  "commits": []
                }
                """);

        verify(apiInventoryImportService, never()).importFromRepositoryBranch(eq(repositoryInfo), eq("main"));
    }
}
