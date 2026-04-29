package flowops.execution.repository;

import flowops.execution.domain.entity.Execution;
import flowops.testcase.domain.entity.TestLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    @Query("""
            select e
            from Execution e
            where (:environmentId is null or e.environment.id = :environmentId)
              and (:testLevel is null or e.testLevel = :testLevel)
              and (:keyword is null or lower(e.createdBy) like lower(concat('%', :keyword, '%')))
              and (:failedOnly = false or e.failedCount > 0)
              and (:slowOnly = false or coalesce(e.avgDurationMs, 0) > 200)
            order by e.createdAt desc
            """)
    Page<Execution> findByFilters(
            @Param("environmentId") Long environmentId,
            @Param("testLevel") TestLevel testLevel,
            @Param("keyword") String keyword,
            @Param("failedOnly") boolean failedOnly,
            @Param("slowOnly") boolean slowOnly,
            Pageable pageable
    );

    @Query("""
            select max(e.endedAt)
            from Execution e
            where e.executionType = flowops.execution.domain.entity.ExecutionType.API
              and e.targetId = :apiId
            """)
    Optional<LocalDateTime> findLatestEndedAtByApiEndpointId(@Param("apiId") Long apiId);

    @Query("""
            select max(e.endedAt)
            from Execution e
            where e.environment.id = :environmentId
            """)
    Optional<LocalDateTime> findLatestEndedAtByEnvironmentId(@Param("environmentId") Long environmentId);

    List<Execution> findTop20ByOrderByCreatedAtDesc();

    @Query("""
            select e
            from Execution e
            where e.app.id = :appId
              and (:environmentId is null or e.environment.id = :environmentId)
              and e.createdAt >= :from
              and e.createdAt < :to
            order by e.createdAt desc
            """)
    List<Execution> findDashboardExecutions(
            @Param("appId") Long appId,
            @Param("environmentId") Long environmentId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
