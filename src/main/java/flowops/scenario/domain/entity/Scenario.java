package flowops.scenario.domain.entity;

import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.Environment;
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
@Table(name = "scenarios")
/**
 * 여러 API 호출을 업무 흐름 단위로 묶은 테스트 시나리오입니다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scenario extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScenarioType type;

    @Column(name = "recommendation_reason", length = 2000)
    private String recommendationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScenarioSource source;

    @Builder
    private Scenario(
            App app,
            Environment environment,
            String name,
            String description,
            ScenarioType type,
            String recommendationReason,
            ScenarioSource source
    ) {
        this.app = app;
        this.environment = environment;
        this.name = name;
        this.description = description;
        this.type = type;
        this.recommendationReason = recommendationReason;
        this.source = source;
    }

    public void update(String name, String description, ScenarioType type, String recommendationReason) {
        this.name = name;
        this.description = description;
        this.type = type;
        this.recommendationReason = recommendationReason;
    }
}
