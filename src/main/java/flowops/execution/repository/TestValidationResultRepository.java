package flowops.execution.repository;

import flowops.execution.domain.entity.TestValidationResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestValidationResultRepository extends JpaRepository<TestValidationResult, Long> {

    List<TestValidationResult> findByExecutionStepIdOrderByIdAsc(Long executionStepId);
}
