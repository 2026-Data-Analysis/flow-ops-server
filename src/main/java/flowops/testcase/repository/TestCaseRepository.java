package flowops.testcase.repository;

import flowops.testcase.domain.entity.TestCase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
