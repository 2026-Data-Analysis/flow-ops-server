package flowops.testcase.repository;

import flowops.testcase.domain.entity.TestCaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, Long> {
}
