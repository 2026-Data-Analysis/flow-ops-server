package flowops.environment.domain.entity;

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
@Table(name = "environment_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvironmentConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvironmentType type;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "secret_reference", length = 255)
    private String secretReference;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Builder
    private EnvironmentConfig(
            Project project,
            String name,
            EnvironmentType type,
            String baseUrl,
            String secretReference,
            boolean isDefault
    ) {
        this.project = project;
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.secretReference = secretReference;
        this.isDefault = isDefault;
    }
}
