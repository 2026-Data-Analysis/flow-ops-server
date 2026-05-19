package flowops.environment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.github.domain.entity.RepositoryInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnvironmentProvisioningServiceTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @InjectMocks
    private EnvironmentProvisioningService environmentProvisioningService;

    @Test
    void createsEnvironmentWhenSameBranchExistsForDifferentRepository() {
        App app = app(1L);
        RepositoryInfo repositoryInfo = repositoryInfo(10L);
        when(environmentRepository.existsByAppIdAndRepositoryInfoIdAndBranchName(1L, 10L, "main"))
                .thenReturn(false);

        environmentProvisioningService.ensureBranchEnvironment(app, repositoryInfo, "main", true);

        ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);
        verify(environmentRepository).save(captor.capture());
        Environment environment = captor.getValue();
        assertThat(environment.getApp()).isSameAs(app);
        assertThat(environment.getRepositoryInfo()).isSameAs(repositoryInfo);
        assertThat(environment.getBranchName()).isEqualTo("main");
    }

    @Test
    void skipsEnvironmentWhenSameRepositoryBranchAlreadyExists() {
        App app = app(1L);
        RepositoryInfo repositoryInfo = repositoryInfo(10L);
        when(environmentRepository.existsByAppIdAndRepositoryInfoIdAndBranchName(1L, 10L, "main"))
                .thenReturn(true);

        environmentProvisioningService.ensureBranchEnvironment(app, repositoryInfo, "main", true);

        verify(environmentRepository, never()).save(any());
    }

    private App app(Long id) {
        App app = App.builder()
                .name("FlowOps")
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private RepositoryInfo repositoryInfo(Long id) {
        RepositoryInfo repositoryInfo = RepositoryInfo.builder()
                .repositoryName("backend")
                .fullName("flowops/backend")
                .repositoryUrl("https://github.com/flowops/backend")
                .defaultBranch("main")
                .build();
        ReflectionTestUtils.setField(repositoryInfo, "id", id);
        return repositoryInfo;
    }
}
