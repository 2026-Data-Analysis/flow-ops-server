package flowops.execution.domain.entity;

import flowops.testcase.domain.entity.TestCase;
import flowops.scenario.domain.entity.ScenarioStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "execution_step_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionStepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private Execution execution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id")
    private TestCase testCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_step_id")
    private ScenarioStep scenarioStep;

    @Column(name = "step_order")
    private Integer stepOrder;

    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    @Column(length = 10)
    private String method;

    @Column(length = 500)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStepStatus status;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private ExecutionStepLog(
            Execution execution,
            TestCase testCase,
            ScenarioStep scenarioStep,
            Integer stepOrder,
            String stepName,
            String method,
            String path,
            ExecutionStepStatus status,
            String requestBody,
            String responseBody,
            Integer responseCode,
            Long durationMs,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt
    ) {
        this.execution = execution;
        this.testCase = testCase;
        this.scenarioStep = scenarioStep;
        this.stepOrder = stepOrder;
        this.stepName = stepName;
        this.method = method;
        this.path = path;
        this.status = status;
        this.requestBody = requestBody;
        this.responseBody = responseBody;
        this.responseCode = responseCode;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
    }
}
