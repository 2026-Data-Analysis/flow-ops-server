package flowops.scenario.domain.entity;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.apiinventory.domain.entity.ApiInventory;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_inventory_id")
    private ApiInventory apiInventory;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "step_id", length = 100)
    private String stepId;

    @Column(name = "step_ref", length = 100)
    private String ref;

    @Column(name = "chained_variables", columnDefinition = "TEXT")
    private String chainedVariables;

    @Column(name = "step_type", length = 40)
    private String type;

    @Column(name = "test_level", length = 20)
    private String testLevel;

    @Column(name = "user_role", length = 100)
    private String userRole;

    @Column(name = "state_condition", columnDefinition = "TEXT")
    private String stateCondition;

    @Column(name = "data_variant", columnDefinition = "TEXT")
    private String dataVariant;

    @Column(name = "request_spec", columnDefinition = "TEXT")
    private String requestSpec;

    @Column(name = "expected_spec", columnDefinition = "TEXT")
    private String expectedSpec;

    @Column(name = "assertion_spec", columnDefinition = "TEXT")
    private String assertionSpec;

    @Column(name = "is_duplicate")
    private Boolean duplicate;

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
            ApiInventory apiInventory,
            String label,
            String stepId,
            String ref,
            String chainedVariables,
            String type,
            String testLevel,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec,
            Boolean duplicate,
            String requestConfig,
            String extractRules,
            String validationRules
    ) {
        this.scenario = scenario;
        this.stepOrder = stepOrder;
        this.apiEndpoint = apiEndpoint;
        this.apiInventory = apiInventory;
        this.label = label;
        this.stepId = stepId;
        this.ref = ref;
        this.chainedVariables = chainedVariables;
        this.type = type;
        this.testLevel = testLevel;
        this.userRole = userRole;
        this.stateCondition = stateCondition;
        this.dataVariant = dataVariant;
        this.requestSpec = requestSpec;
        this.expectedSpec = expectedSpec;
        this.assertionSpec = assertionSpec;
        this.duplicate = duplicate;
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

    public void updateStepSpec(
            String stepId,
            String ref,
            String chainedVariables,
            String type,
            String testLevel,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec,
            Boolean duplicate
    ) {
        this.stepId = stepId;
        this.ref = ref;
        this.chainedVariables = chainedVariables;
        this.type = type;
        this.testLevel = testLevel;
        this.userRole = userRole;
        this.stateCondition = stateCondition;
        this.dataVariant = dataVariant;
        this.requestSpec = requestSpec;
        this.expectedSpec = expectedSpec;
        this.assertionSpec = assertionSpec;
        this.duplicate = duplicate;
    }
}
