package flowops.aiintegration.repository;

import flowops.aiintegration.domain.entity.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
}
