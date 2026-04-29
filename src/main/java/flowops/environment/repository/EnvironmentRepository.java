package flowops.environment.repository;

import flowops.environment.domain.entity.Environment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, Long> {

    List<Environment> findByAppIdOrderByCreatedAtDesc(Long appId);

    boolean existsByAppIdAndBranchName(Long appId, String branchName);
}
