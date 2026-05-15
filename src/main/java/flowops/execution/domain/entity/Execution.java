package flowops.execution.domain.entity;

import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.ExecutionMode;
import flowops.testcase.domain.entity.TestLevel;
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
@Table(name = "executions")
/**
 * API, 테스트케이스, 시나리오 실행의 상태와 집계 결과를 저장합니다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false, length = 20)
    private ExecutionType executionType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private ExecutionTriggerSource triggerSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "execution_mode", nullable = false, length = 20)
    private ExecutionMode executionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_level", nullable = false, length = 20)
    private TestLevel testLevel;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionStatus status;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount;

    @Column(name = "passed_count", nullable = false)
    private Integer passedCount;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount;

    @Column(name = "avg_duration_ms")
    private Long avgDurationMs;

    @Column(name = "total_duration_ms")
    private Long totalDurationMs;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Execution(
            App app,
            Environment environment,
            ExecutionType executionType,
            Long targetId,
            ExecutionTriggerSource triggerSource,
            ExecutionMode executionMode,
            TestLevel testLevel,
            String name,
            ExecutionStatus status,
            Integer totalCount,
            Integer passedCount,
            Integer failedCount,
            Long avgDurationMs,
            Long totalDurationMs,
            String summary,
            String createdBy,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            LocalDateTime createdAt
    ) {
        this.app = app;
        this.environment = environment;
        this.executionType = executionType;
        this.targetId = targetId;
        this.triggerSource = triggerSource;
        this.executionMode = executionMode;
        this.testLevel = testLevel;
        this.name = name;
        this.status = status;
        this.totalCount = totalCount;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.avgDurationMs = avgDurationMs;
        this.totalDurationMs = totalDurationMs;
        this.summary = summary;
        this.createdBy = createdBy;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
    }

    public void markRunning() {
        this.status = ExecutionStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int totalCount, int passedCount, int failedCount, long avgDurationMs, long totalDurationMs, String summary) {
        this.totalCount = totalCount;
        this.passedCount = passedCount;
        this.failedCount = failedCount;
        this.avgDurationMs = avgDurationMs;
        this.totalDurationMs = totalDurationMs;
        this.summary = summary;
        this.status = failedCount == 0 ? ExecutionStatus.SUCCESS : (passedCount > 0 ? ExecutionStatus.PARTIAL_FAILED : ExecutionStatus.FAILED);
        this.endedAt = LocalDateTime.now();
    }

    public void complete(int totalCount, int passedCount, int failedCount, long avgDurationMs) {
        long totalDuration = avgDurationMs * Math.max(totalCount, 0);
        complete(totalCount, passedCount, failedCount, avgDurationMs, totalDuration, null);
    }

    public void cancel() {
        this.status = ExecutionStatus.CANCELED;
        this.endedAt = LocalDateTime.now();
    }
}
