package flowops.execution.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "test_validation_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestValidationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_step_id", nullable = false)
    private ExecutionStepLog executionStep;

    @Column(name = "assertion_name", nullable = false, length = 200)
    private String assertionName;

    @Column(name = "expected_value", columnDefinition = "TEXT")
    private String expectedValue;

    @Column(name = "actual_value", columnDefinition = "TEXT")
    private String actualValue;

    @Column(nullable = false)
    private boolean passed;

    @Column(length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private TestValidationResult(
            ExecutionStepLog executionStep,
            String assertionName,
            String expectedValue,
            String actualValue,
            boolean passed,
            String message,
            LocalDateTime createdAt
    ) {
        this.executionStep = executionStep;
        this.assertionName = assertionName;
        this.expectedValue = expectedValue;
        this.actualValue = actualValue;
        this.passed = passed;
        this.message = message;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
