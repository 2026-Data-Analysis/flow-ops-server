package flowops.testcase.repository;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.testcase.domain.entity.TestCase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    List<TestCase> findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(Long apiId);

    List<TestCase> findByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(Long apiInventoryId);

    List<TestCase> findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List<Long> apiIds);

    List<TestCase> findByAppIdAndActiveTrueOrderByUpdatedAtDesc(Long appId);

    List<TestCase> findTop3ByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(Long apiId);

    List<TestCase> findTop3ByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(Long apiInventoryId);

    Optional<TestCase> findByIdAndActiveTrue(Long testCaseId);

    long countByApiEndpointIdAndActiveTrue(Long apiId);

    long countByApiInventoryIdAndActiveTrue(Long apiInventoryId);

    @Query("""
            SELECT COUNT(tc) FROM TestCase tc
            JOIN tc.apiInventory ai
            WHERE ai.repositoryInfo.id = :repositoryId
              AND ai.method = :method
              AND ai.endpointPath = :endpointPath
              AND tc.active = true
            """)
    long countByRepositoryAndMethodAndPathAndActiveTrue(
            @Param("repositoryId") Long repositoryId,
            @Param("method") ApiHttpMethod method,
            @Param("endpointPath") String endpointPath
    );

    @Query("""
            SELECT tc FROM TestCase tc
            JOIN tc.apiInventory ai
            WHERE ai.repositoryInfo.id = :repositoryId
              AND ai.method = :method
              AND ai.endpointPath = :endpointPath
              AND tc.active = true
            ORDER BY tc.updatedAt DESC
            """)
    List<TestCase> findByRepositoryAndMethodAndPathAndActiveTrueOrderByUpdatedAtDesc(
            @Param("repositoryId") Long repositoryId,
            @Param("method") ApiHttpMethod method,
            @Param("endpointPath") String endpointPath
    );
}
