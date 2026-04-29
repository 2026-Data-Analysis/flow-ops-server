package flowops.execution.repository;

import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionStepLogRepository extends JpaRepository<ExecutionStepLog, Long> {

    List<ExecutionStepLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    List<ExecutionStepLog> findByExecutionIdAndStatusOrderByCreatedAtAsc(Long executionId, ExecutionStepStatus status);

    List<ExecutionStepLog> findByExecutionIdIn(List<Long> executionIds);

    long countByTestCaseApiEndpointId(Long apiId);

    long countByTestCaseApiEndpointIdAndStatus(Long apiId, ExecutionStepStatus status);
}
