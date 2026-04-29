package flowops.testgeneration.repository;

import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedTestCaseDraftRepository extends JpaRepository<GeneratedTestCaseDraft, Long> {

    List<GeneratedTestCaseDraft> findByGenerationIdOrderByCreatedAtAsc(Long generationId);

    List<GeneratedTestCaseDraft> findByGenerationIdAndIdIn(Long generationId, List<Long> draftIds);
}
