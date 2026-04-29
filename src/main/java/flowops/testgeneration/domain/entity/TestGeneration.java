package flowops.testgeneration.domain.entity;

import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.Environment;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "test_generations")
/**
 * AI 테스트 생성 요청의 진행 상태와 생성 결과 집계를 저장합니다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestGeneration {

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
    @Column(nullable = false, length = 20)
    private TestGenerationStatus status;

    @Column(name = "requested_by", nullable = false, length = 120)
    private String requestedBy;

    @Column(name = "context_summary", length = 4000)
    private String contextSummary;

    @Column(name = "current_coverage", precision = 5, scale = 2)
    private BigDecimal currentCoverage;

    @Column(name = "predicted_coverage", precision = 5, scale = 2)
    private BigDecimal predictedCoverage;

    @Column(name = "existing_count")
    private Integer existingCount;

    @Column(name = "new_count")
    private Integer newCount;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private TestGeneration(
            App app,
            Environment environment,
            TestGenerationStatus status,
            String requestedBy,
            String contextSummary,
            BigDecimal currentCoverage,
            BigDecimal predictedCoverage,
            Integer existingCount,
            Integer newCount,
            Integer duplicateCount,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        this.app = app;
        this.environment = environment;
        this.status = status;
        this.requestedBy = requestedBy;
        this.contextSummary = contextSummary;
        this.currentCoverage = currentCoverage;
        this.predictedCoverage = predictedCoverage;
        this.existingCount = existingCount;
        this.newCount = newCount;
        this.duplicateCount = duplicateCount;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public void markProcessing() {
        this.status = TestGenerationStatus.PROCESSING;
    }

    public void markCompleted(int existingCount, int newCount, int duplicateCount, BigDecimal predictedCoverage) {
        this.status = TestGenerationStatus.COMPLETED;
        this.existingCount = existingCount;
        this.newCount = newCount;
        this.duplicateCount = duplicateCount;
        this.predictedCoverage = predictedCoverage;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = TestGenerationStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }
}
