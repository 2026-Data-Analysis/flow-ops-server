package flowops.environment.repository;

import flowops.environment.domain.entity.TriggerRule;
import flowops.environment.domain.entity.TriggerType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriggerRuleRepository extends JpaRepository<TriggerRule, Long> {

    List<TriggerRule> findByEnvironmentIdOrderByCreatedAtDesc(Long environmentId);

    List<TriggerRule> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);
}
