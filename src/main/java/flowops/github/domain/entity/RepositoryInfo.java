package flowops.github.domain.entity;

import flowops.global.common.BaseEntity;
import flowops.project.domain.entity.Project;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Getter
@Entity
@Table(name = "project_repositories")
@Comment("프로젝트에 연결된 GitHub 저장소 정보")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepositoryInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("저장소 식별자")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @Comment("저장소가 연결된 프로젝트")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Comment("저장소 제공자")
    private RepositoryProvider provider;

    @Column(name = "repository_name", nullable = false, length = 150)
    @Comment("저장소 이름")
    private String repositoryName;

    @Column(name = "full_name", nullable = false, unique = true, length = 255)
    @Comment("owner/repository 형식의 전체 저장소 이름")
    private String fullName;

    @Column(name = "repository_url", nullable = false, length = 500)
    @Comment("저장소 HTML URL")
    private String repositoryUrl;

    @Column(name = "default_branch", nullable = false, length = 100)
    @Comment("GitHub 기본 브랜치")
    private String defaultBranch;

    @Column(name = "external_repository_id", length = 100)
    @Comment("GitHub 저장소 ID")
    private String externalRepositoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 30)
    @Comment("저장소 연결 상태")
    private RepositoryConnectionStatus connectionStatus;

    @Column(name = "last_synced_at")
    @Comment("마지막 동기화 시각")
    private LocalDateTime lastSyncedAt;

    @OneToMany(mappedBy = "repositoryInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Comment("저장소에서 조회한 브랜치 목록")
    private List<RepositoryBranch> branches = new ArrayList<>();

    @Builder
    private RepositoryInfo(
            Project project,
            RepositoryProvider provider,
            String repositoryName,
            String fullName,
            String repositoryUrl,
            String defaultBranch,
            String externalRepositoryId,
            RepositoryConnectionStatus connectionStatus,
            LocalDateTime lastSyncedAt
    ) {
        this.project = project;
        this.provider = provider;
        this.repositoryName = repositoryName;
        this.fullName = fullName;
        this.repositoryUrl = repositoryUrl;
        this.defaultBranch = defaultBranch;
        this.externalRepositoryId = externalRepositoryId;
        this.connectionStatus = connectionStatus;
        this.lastSyncedAt = lastSyncedAt;
    }

    public void addBranch(String branchName, boolean selected, boolean defaultBranch) {
        branches.add(RepositoryBranch.builder()
                .repositoryInfo(this)
                .branchName(branchName)
                .selected(selected)
                .defaultBranch(defaultBranch)
                .build());
    }

    public String getOwner() {
        int separatorIndex = fullName.indexOf('/');
        return separatorIndex < 0 ? "" : fullName.substring(0, separatorIndex);
    }
}
