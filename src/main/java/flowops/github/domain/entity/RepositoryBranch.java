package flowops.github.domain.entity;

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
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "repository_branches")
@Comment("GitHub 저장소에서 조회한 브랜치 정보")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepositoryBranch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("저장소 브랜치 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    @Comment("브랜치가 속한 저장소")
    private RepositoryInfo repositoryInfo;

    @Column(name = "branch_name", nullable = false, length = 100)
    @Comment("브랜치 이름")
    private String branchName;

    @Column(nullable = false)
    @Comment("API 명세 파싱 대상으로 선택된 브랜치 여부")
    private boolean selected;

    @Column(name = "default_branch", nullable = false)
    @Comment("GitHub 기본 브랜치 여부")
    private boolean defaultBranch;

    @Builder
    private RepositoryBranch(
            RepositoryInfo repositoryInfo,
            String branchName,
            boolean selected,
            boolean defaultBranch
    ) {
        this.repositoryInfo = repositoryInfo;
        this.branchName = branchName;
        this.selected = selected;
        this.defaultBranch = defaultBranch;
    }
}
