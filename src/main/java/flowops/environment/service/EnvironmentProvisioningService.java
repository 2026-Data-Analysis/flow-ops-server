package flowops.environment.service;

import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.AuthType;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.environment.repository.EnvironmentRepository;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.testcase.domain.entity.TestLevel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentProvisioningService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentProvisioningService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    @Transactional
    public void ensureBranchEnvironment(App app, RepositoryInfo repositoryInfo, String branchName, boolean defaultBranch) {
        if (branchName == null || branchName.isBlank() || environmentRepository.existsByAppIdAndBranchName(app.getId(), branchName)) {
            return;
        }
        environmentRepository.save(Environment.builder()
                .app(app)
                .repositoryInfo(repositoryInfo)
                .name(branchName.length() > 30 ? branchName.substring(0, 30) : branchName)
                .branchName(branchName)
                .baseUrl("http://localhost:8080")
                .authType(AuthType.NONE)
                .headers(null)
                .defaultTestLevel(defaultBranch ? TestLevel.SMOKE : TestLevel.REGRESSION)
                .defaultTestLevelSource(TestLevelSource.AI_RECOMMENDED)
                .build());
    }
}
