package flowops.environment.repository;

import flowops.environment.domain.entity.Environment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    List<Environment> findByAppIdOrderByCreatedAtDesc(Long appId);

    java.util.Optional<Environment> findFirstByAppIdOrderByCreatedAtAsc(Long appId);

    boolean existsByAppIdAndBranchName(Long appId, String branchName);

    java.util.Optional<Environment> findFirstByAppIdAndBranchNameOrderByCreatedAtAsc(Long appId, String branchName);

    boolean existsByAppIdAndRepositoryInfoIdAndBranchName(Long appId, Long repositoryId, String branchName);
}
