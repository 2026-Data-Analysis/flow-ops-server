package flowops.environment.domain.entity;

import flowops.app.domain.entity.App;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.global.common.BaseEntity;
import flowops.testcase.domain.entity.TestLevel;
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
@Table(name = "environments")
@Comment("브랜치별 API 실행 환경과 인증, 기본 테스트 레벨 설정")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Environment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("환경 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_id", nullable = false)
    @Comment("환경이 속한 앱")
    private App app;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    @Comment("환경과 연결된 GitHub 저장소")
    private RepositoryInfo repositoryInfo;

    @Column(nullable = false, length = 30)
    @Comment("환경 이름")
    private String name;

    @Column(name = "branch_name", length = 100)
    @Comment("환경과 연결된 브랜치 이름")
    private String branchName;

    @Column(name = "base_url", nullable = false, length = 1000)
    @Comment("API 기본 URL")
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    @Comment("인증 방식")
    private AuthType authType;

    @Column(name = "auth_config", columnDefinition = "TEXT")
    @Comment("암호화 또는 참조 형태로 저장되는 인증 설정")
    private String authConfig;

    @Column(columnDefinition = "TEXT")
    @Comment("기본 요청 헤더 JSON")
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_test_level", nullable = false, length = 20)
    @Comment("기본 테스트 위계")
    private TestLevel defaultTestLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_test_level_source", nullable = false, length = 30)
    @Comment("기본 테스트 위계 결정 출처")
    private TestLevelSource defaultTestLevelSource;

    @Builder
    private Environment(
            App app,
            RepositoryInfo repositoryInfo,
            String name,
            String branchName,
            String baseUrl,
            AuthType authType,
            String authConfig,
            String headers,
            TestLevel defaultTestLevel,
            TestLevelSource defaultTestLevelSource
    ) {
        this.app = app;
        this.repositoryInfo = repositoryInfo;
        this.name = name;
        this.branchName = branchName;
        this.baseUrl = baseUrl;
        this.authType = authType;
        this.authConfig = authConfig;
        this.headers = headers;
        this.defaultTestLevel = defaultTestLevel;
        this.defaultTestLevelSource = defaultTestLevelSource == null ? TestLevelSource.MANUAL : defaultTestLevelSource;
    }

    public void update(
            String name,
            String branchName,
            String baseUrl,
            AuthType authType,
            String authConfig,
            String headers,
            TestLevel defaultTestLevel,
            TestLevelSource defaultTestLevelSource
    ) {
        this.name = name;
        this.branchName = branchName;
        this.baseUrl = baseUrl;
        this.authType = authType;
        this.authConfig = authConfig;
        this.headers = headers;
        this.defaultTestLevel = defaultTestLevel;
        this.defaultTestLevelSource = defaultTestLevelSource == null ? TestLevelSource.MANUAL : defaultTestLevelSource;
    }
}
