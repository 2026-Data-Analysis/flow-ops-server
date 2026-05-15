package flowops.scenario.repository;

import flowops.scenario.domain.entity.Scenario;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    List<Scenario> findByAppIdOrderByUpdatedAtDesc(Long appId);

    List<Scenario> findByAppIdAndEnvironmentIdOrderByUpdatedAtDesc(Long appId, Long environmentId);
}
