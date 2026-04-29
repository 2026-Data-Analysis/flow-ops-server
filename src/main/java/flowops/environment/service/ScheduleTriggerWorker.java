package flowops.environment.service;

import flowops.environment.domain.entity.TriggerRule;
import flowops.environment.domain.entity.TriggerType;
import flowops.environment.repository.TriggerRuleRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 스케줄 트리거를 주기적으로 확인하고 자동 테스트를 실행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleTriggerWorker {

    private final TriggerRuleRepository triggerRuleRepository;
    private final TriggerExecutionService triggerExecutionService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void runScheduledTriggers() {
        List<TriggerRule> rules = triggerRuleRepository.findByTriggerTypeAndEnabledTrue(TriggerType.SCHEDULE);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        for (TriggerRule rule : rules) {
            try {
                if (!shouldRun(rule, now)) {
                    continue;
                }
                triggerExecutionService.executeScheduledTrigger(rule);
            } catch (Exception exception) {
                log.warn("Failed to execute scheduled trigger rule {}", rule.getId(), exception);
            }
        }
    }

    private boolean shouldRun(TriggerRule rule, LocalDateTime now) {
        String cron = triggerExecutionService.resolveCronExpression(rule);
        if (cron == null || cron.isBlank()) {
            return false;
        }
        CronExpression expression = CronExpression.parse(cron);
        LocalDateTime previousMinute = now.minusMinutes(1);
        LocalDateTime nextAfterPrevious = expression.next(previousMinute.minusSeconds(1));
        if (nextAfterPrevious == null || nextAfterPrevious.truncatedTo(ChronoUnit.MINUTES).isAfter(now)) {
            return false;
        }
        if (!nextAfterPrevious.truncatedTo(ChronoUnit.MINUTES).equals(now)) {
            return false;
        }
        return rule.getLastTriggeredAt() == null
                || rule.getLastTriggeredAt().truncatedTo(ChronoUnit.MINUTES).isBefore(now);
    }
}
