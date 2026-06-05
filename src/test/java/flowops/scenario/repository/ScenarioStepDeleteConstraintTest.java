package flowops.scenario.repository;

import static org.assertj.core.api.Assertions.assertThat;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.app.domain.entity.App;
import flowops.app.repository.AppRepository;
import flowops.environment.domain.entity.ExecutionMode;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.ExecutionTriggerSource;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.JpaAuditingConfig;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.testcase.domain.entity.TestLevel;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ScenarioStepDeleteConstraintTest {

    @Autowired
    private AppRepository appRepository;

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private ScenarioRepository scenarioRepository;

    @Autowired
    private ScenarioStepRepository scenarioStepRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionStepLogRepository executionStepLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deletingScenarioStepKeepsExecutionLogAndNullsStepReference() {
        App app = appRepository.save(App.builder()
                .name("FlowOps")
                .build());
        ApiEndpoint endpoint = apiEndpointRepository.save(ApiEndpoint.builder()
                .app(app)
                .method(ApiMethod.GET)
                .path("/health")
                .deprecated(false)
                .build());
        Scenario scenario = scenarioRepository.save(Scenario.builder()
                .app(app)
                .name("Health scenario")
                .type(ScenarioType.HAPPY_PATH)
                .source(ScenarioSource.CUSTOM)
                .build());
        ScenarioStep step = scenarioStepRepository.save(ScenarioStep.builder()
                .scenario(scenario)
                .stepOrder(1)
                .apiEndpoint(endpoint)
                .label("Check health")
                .build());
        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .executionType(ExecutionType.SCENARIO)
                .targetId(scenario.getId())
                .triggerSource(ExecutionTriggerSource.MANUAL)
                .executionMode(ExecutionMode.RUN_EXISTING)
                .testLevel(TestLevel.SMOKE)
                .name("Scenario run")
                .status(ExecutionStatus.SUCCESS)
                .totalCount(1)
                .passedCount(1)
                .failedCount(0)
                .tearDownMode(false)
                .createdBy("tester")
                .createdAt(LocalDateTime.now())
                .build());
        ExecutionStepLog log = executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .scenarioStep(step)
                .stepOrder(1)
                .stepName("Check health")
                .method("GET")
                .path("/health")
                .status(ExecutionStepStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build());

        Long stepId = step.getId();
        Long logId = log.getId();
        entityManager.flush();
        entityManager.clear();

        executionStepLogRepository.clearScenarioStepReferencesByScenarioId(scenario.getId());
        ScenarioStep savedStep = scenarioStepRepository.findById(stepId).orElseThrow();
        scenarioStepRepository.delete(savedStep);
        entityManager.flush();
        entityManager.clear();

        ExecutionStepLog savedLog = executionStepLogRepository.findById(logId).orElseThrow();
        assertThat(savedLog.getScenarioStep()).isNull();
    }
}
