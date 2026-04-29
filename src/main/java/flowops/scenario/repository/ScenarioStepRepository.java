package flowops.scenario.repository;

import flowops.scenario.domain.entity.ScenarioStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioStepRepository extends JpaRepository<ScenarioStep, Long> {

    List<ScenarioStep> findByScenarioIdOrderByStepOrderAsc(Long scenarioId);

    long countByScenarioId(Long scenarioId);
}
