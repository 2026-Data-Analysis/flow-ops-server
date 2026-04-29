package flowops.apiinventory.domain.entity;

import flowops.github.domain.entity.RepositoryInfo;
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
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "api_inventories")
@Comment("명세 파일 또는 수동 입력으로 수집한 API 목록")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiInventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("API 인벤토리 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Comment("API가 속한 프로젝트")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @Comment("API를 파싱한 저장소")
    private RepositoryInfo repositoryInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Comment("HTTP 메서드")
    private ApiHttpMethod method;

    @Column(name = "endpoint_path", nullable = false, length = 500)
    @Comment("API 엔드포인트 경로")
    private String endpointPath;

    @Column(name = "operation_id", length = 150)
    @Comment("OpenAPI operationId")
    private String operationId;

    @Column(name = "branch_name", length = 100)
    @Comment("API 명세가 파싱된 브랜치 이름")
    private String branchName;

    @Column(length = 500)
    @Comment("API 요약")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    @Comment("API 수집 출처")
    private ApiInventorySource sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Comment("API 인벤토리 상태")
    private ApiInventoryStatus status;

    @Column(name = "spec_version", length = 50)
    @Comment("명세 버전")
    private String specVersion;

    @Column(name = "auth_required", nullable = false)
    @Comment("인증 필요 여부")
    private boolean authRequired;

    @Builder
    private ApiInventory(
            Project project,
            RepositoryInfo repositoryInfo,
            ApiHttpMethod method,
            String endpointPath,
            String operationId,
            String branchName,
            String summary,
            ApiInventorySource sourceType,
            ApiInventoryStatus status,
            String specVersion,
            boolean authRequired
    ) {
        this.project = project;
        this.repositoryInfo = repositoryInfo;
        this.method = method;
        this.endpointPath = endpointPath;
        this.operationId = operationId;
        this.branchName = branchName;
        this.summary = summary;
        this.sourceType = sourceType;
        this.status = status;
        this.specVersion = specVersion;
        this.authRequired = authRequired;
    }
}
