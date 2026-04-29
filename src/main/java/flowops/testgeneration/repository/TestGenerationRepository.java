package flowops.testgeneration.repository;

import flowops.testgeneration.domain.entity.TestGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestGenerationRepository extends JpaRepository<TestGeneration, Long> {
}
