package flowops.aiintegration.domain.entity;

import flowops.global.common.BaseEntity;
import flowops.project.domain.entity.Project;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSuggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggestion_type", nullable = false, length = 40)
    private AiSuggestionType suggestionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiSuggestionStatus status;

    @Column(name = "input_text", columnDefinition = "TEXT")
    private String inputText;

    @Column(name = "output_text", columnDefinition = "TEXT")
    private String outputText;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "source_reference", length = 255)
    private String sourceReference;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Builder
    private AiSuggestion(
            Project project,
            AiSuggestionType suggestionType,
            AiSuggestionStatus status,
            String inputText,
            String outputText,
            String modelName,
            String sourceReference,
            String failureReason
    ) {
        this.project = project;
        this.suggestionType = suggestionType;
        this.status = status;
        this.inputText = inputText;
        this.outputText = outputText;
        this.modelName = modelName;
        this.sourceReference = sourceReference;
        this.failureReason = failureReason;
    }
}
