package flowops.app.domain.entity;

import flowops.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "apps")
/**
 * 테스트 자동화 대상 애플리케이션을 표현합니다.
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class App extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "repo_url", length = 1000)
    private String repoUrl;

    @Column(name = "spec_source", length = 500)
    private String specSource;

    @Column(name = "default_branch", length = 100)
    private String defaultBranch;

    @Builder
    private App(String name, String repoUrl, String specSource, String defaultBranch) {
        this.name = name;
        this.repoUrl = repoUrl;
        this.specSource = specSource;
        this.defaultBranch = defaultBranch;
    }
}
