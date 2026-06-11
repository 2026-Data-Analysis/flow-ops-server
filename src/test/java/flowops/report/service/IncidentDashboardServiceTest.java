package flowops.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.ExecutionMode;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.ExecutionTriggerSource;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.report.dto.response.IncidentDashboardResponse;
import flowops.testcase.domain.entity.TestLevel;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IncidentDashboardServiceTest {

    @Mock
    private AppService appService;

    @Mock
    private ExecutionRepository executionRepository;

    @Mock
    private ExecutionStepLogRepository executionStepLogRepository;

    @InjectMocks
    private IncidentDashboardService incidentDashboardService;

    @Test
    void calculatesDashboardCountsFromStepLogsWhenExecutionIsPartiallyFailed() {
        App app = App.builder().name("FlowOps").build();
        ReflectionTestUtils.setField(app, "id", 1L);
        Execution execution = execution(100L, app, ExecutionStatus.FAILED, 2, 0, 2);
        ExecutionStepLog passedLog = log(1000L, execution, ExecutionStepStatus.SUCCESS, "GET", "/health", 200);
        ExecutionStepLog failedLog = log(1001L, execution, ExecutionStepStatus.FAILED, "POST", "/orders", 500);

        when(appService.getApp(1L)).thenReturn(app);
        when(executionRepository.findDashboardExecutions(eq(1L), eq(null), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(execution))
                .thenReturn(List.of());
        when(executionStepLogRepository.findByExecutionIdIn(List.of(100L))).thenReturn(List.of(passedLog, failedLog));

        IncidentDashboardResponse response = incidentDashboardService.getDashboard(
                1L,
                null,
                null,
                LocalDate.now().minusDays(1),
                LocalDate.now(),
                1
        );

        assertThat(response.totalTests()).isEqualTo(2);
        assertThat(response.failedTests()).isEqualTo(1);
        assertThat(response.successRate()).isEqualTo(50.0);
        assertThat(response.failureRate().value()).isEqualTo(50.0);
        assertThat(response.testResultsTrend())
                .anySatisfy(point -> {
                    assertThat(point.passedTests()).isEqualTo(1);
                    assertThat(point.failedTests()).isEqualTo(1);
                    assertThat(point.passedApiCount()).isEqualTo(1);
                    assertThat(point.failedApiCount()).isEqualTo(1);
                });
    }

    private Execution execution(
            Long id,
            App app,
            ExecutionStatus status,
            int totalCount,
            int passedCount,
            int failedCount
    ) {
        Execution execution = Execution.builder()
                .app(app)
                .executionType(ExecutionType.API_BATCH)
                .targetId(1L)
                .triggerSource(ExecutionTriggerSource.MANUAL)
                .executionMode(ExecutionMode.RUN_EXISTING)
                .testLevel(TestLevel.REGRESSION)
                .name("Batch")
                .status(status)
                .totalCount(totalCount)
                .passedCount(passedCount)
                .failedCount(failedCount)
                .avgDurationMs(120L)
                .createdBy("tester")
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(execution, "id", id);
        return execution;
    }

    private ExecutionStepLog log(
            Long id,
            Execution execution,
            ExecutionStepStatus status,
            String method,
            String path,
            int responseCode
    ) {
        ExecutionStepLog log = ExecutionStepLog.builder()
                .execution(execution)
                .stepName(method + " " + path)
                .method(method)
                .path(path)
                .status(status)
                .responseCode(responseCode)
                .durationMs(100L)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }
}
