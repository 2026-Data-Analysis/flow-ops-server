package flowops.execution.repository;

import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.testcase.domain.entity.TestLevel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionStepLogRepository extends JpaRepository<ExecutionStepLog, Long> {

    List<ExecutionStepLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    List<ExecutionStepLog> findByExecutionIdAndStatusOrderByCreatedAtAsc(Long executionId, ExecutionStepStatus status);

    List<ExecutionStepLog> findByExecutionIdIn(List<Long> executionIds);

    long countByTestCaseApiEndpointId(Long apiId);

    long countByTestCaseApiEndpointIdAndStatus(Long apiId, ExecutionStepStatus status);

    @Query("""
            select log
            from ExecutionStepLog log
            join fetch log.execution execution
            left join fetch execution.environment environment
            where (:keyword is null
                   or lower(execution.name) like lower(concat('%', :keyword, '%'))
                   or lower(log.stepName) like lower(concat('%', :keyword, '%'))
                   or lower(log.method) like lower(concat('%', :keyword, '%'))
                   or lower(log.path) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(log.errorMessage, '')) like lower(concat('%', :keyword, '%')))
              and (:failedOnly = false or log.status = flowops.execution.domain.entity.ExecutionStepStatus.FAILED)
              and (:slowOnly = false or (log.durationMs is not null and log.durationMs > 200))
              and (:environment is null or lower(environment.name) = lower(:environment))
              and (:testLevel is null or execution.testLevel = :testLevel)
            order by coalesce(log.startedAt, log.createdAt) desc, log.id desc
            """)
    List<ExecutionStepLog> findLogRows(
            @Param("keyword") String keyword,
            @Param("failedOnly") boolean failedOnly,
            @Param("slowOnly") boolean slowOnly,
            @Param("environment") String environment,
            @Param("testLevel") TestLevel testLevel
    );
}
