package flowops.testcase.domain.entity;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.app.domain.entity.App;
import flowops.global.common.BaseEntity;
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
@Table(name = "test_cases")
/**
 * API 검증을 위한 테스트케이스의 현재 버전을 저장합니다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TestCase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "api_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_inventory_id")
    private ApiInventory apiInventory;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TestCaseType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_level", nullable = false, length = 20)
    private TestLevel testLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TestCaseSource source;

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

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Integer version;

    @Builder
    private TestCase(
            App app,
            ApiEndpoint apiEndpoint,
            ApiInventory apiInventory,
            String name,
            String description,
            TestCaseType type,
            TestLevel testLevel,
            TestCaseSource source,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec,
            boolean active,
            Integer version
    ) {
        this.app = app;
        this.apiEndpoint = apiEndpoint;
        this.apiInventory = apiInventory;
        this.name = name;
        this.description = description;
        this.type = type;
        this.testLevel = testLevel;
        this.source = source;
        this.userRole = userRole;
        this.stateCondition = stateCondition;
        this.dataVariant = dataVariant;
        this.requestSpec = requestSpec;
        this.expectedSpec = expectedSpec;
        this.assertionSpec = assertionSpec;
        this.active = active;
        this.version = version;
    }

    public void update(
            String name,
            String description,
            TestCaseType type,
            TestLevel testLevel,
            String userRole,
            String stateCondition,
            String dataVariant,
            String requestSpec,
            String expectedSpec,
            String assertionSpec
    ) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.testLevel = testLevel;
        this.userRole = userRole;
        this.stateCondition = stateCondition;
        this.dataVariant = dataVariant;
        this.requestSpec = requestSpec;
        this.expectedSpec = expectedSpec;
        this.assertionSpec = assertionSpec;
        this.source = TestCaseSource.EDITED;
        this.version = this.version + 1;
    }

    public void deactivate() {
        changeActive(false);
    }

    public void changeActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        this.version = this.version + 1;
    }
}
