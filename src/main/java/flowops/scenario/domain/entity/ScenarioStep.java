package flowops.scenario.domain.entity;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "scenario_steps")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScenarioStep extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "request_config", columnDefinition = "TEXT")
    private String requestConfig;

    @Column(name = "extract_rules", columnDefinition = "TEXT")
    private String extractRules;

    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;

    @Builder
    private ScenarioStep(
            Scenario scenario,
            Integer stepOrder,
            ApiEndpoint apiEndpoint,
            String label,
            String requestConfig,
            String extractRules,
            String validationRules
    ) {
        this.scenario = scenario;
        this.stepOrder = stepOrder;
        this.apiEndpoint = apiEndpoint;
        this.label = label;
        this.requestConfig = requestConfig;
        this.extractRules = extractRules;
        this.validationRules = validationRules;
    }

    public void update(Integer stepOrder, ApiEndpoint apiEndpoint, String label, String requestConfig, String extractRules, String validationRules) {
        this.stepOrder = stepOrder;
        this.apiEndpoint = apiEndpoint;
        this.label = label;
        this.requestConfig = requestConfig;
        this.extractRules = extractRules;
        this.validationRules = validationRules;
    }
}
