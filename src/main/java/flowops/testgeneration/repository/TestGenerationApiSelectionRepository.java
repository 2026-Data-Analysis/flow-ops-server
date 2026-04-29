package flowops.testgeneration.repository;

import flowops.testgeneration.domain.entity.TestGenerationApiSelection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestGenerationApiSelectionRepository extends JpaRepository<TestGenerationApiSelection, Long> {

    List<TestGenerationApiSelection> findByGenerationId(Long generationId);
}
