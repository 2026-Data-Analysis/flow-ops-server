package flowops.github.repository;

import flowops.github.domain.entity.RepositoryInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryInfoRepository extends JpaRepository<RepositoryInfo, Long> {
    Optional<RepositoryInfo> findByFullName(String fullName);

    List<RepositoryInfo> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
