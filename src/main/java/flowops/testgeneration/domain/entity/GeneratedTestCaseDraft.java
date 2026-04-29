package flowops.testgeneration.domain.entity;

import flowops.api.domain.entity.ApiEndpoint;
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
@Table(name = "generated_test_case_drafts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedTestCaseDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_id", nullable = false)
    private TestGeneration generation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(name = "draft_type", length = 40)
    private String type;

    @Column(name = "user_role", length = 100)
    private String userRole;

    @Column(name = "state_condition", length = 2000)
    private String stateCondition;

    @Column(name = "data_variant", length = 1000)
    private String dataVariant;

    @Column(name = "request_spec", columnDefinition = "TEXT")
    private String requestSpec;

    @Column(name = "expected_spec", columnDefinition = "TEXT")
    private String expectedSpec;

    @Column(name = "assertion_spec", columnDefinition = "TEXT")
    private String assertionSpec;

    @Column(name = "is_duplicate", nullable = false)
    private boolean duplicate;

    @Column(name = "selected_for_save", nullable = false)
    private boolean selectedForSave;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private GeneratedTestCaseDraft(
            TestGeneration generation,
            ApiEndpoint apiEndpoint,
            String title,
            String description,
            String type,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec,
            boolean duplicate,
            boolean selectedForSave,
            LocalDateTime createdAt
    ) {
        this.generation = generation;
        this.apiEndpoint = apiEndpoint;
        this.title = title;
        this.description = description;
        this.type = type;
        this.userRole = userRole;
        this.stateCondition = stateCondition;
        this.dataVariant = dataVariant;
        this.requestSpec = requestSpec;
        this.expectedSpec = expectedSpec;
        this.assertionSpec = assertionSpec;
        this.duplicate = duplicate;
        this.selectedForSave = selectedForSave;
        this.createdAt = createdAt;
    }

    public void selectForSave() {
        this.selectedForSave = true;
    }
}
