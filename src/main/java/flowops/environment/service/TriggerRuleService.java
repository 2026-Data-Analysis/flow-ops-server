package flowops.environment.service;

import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.TriggerRule;
import flowops.environment.domain.entity.TriggerType;
import flowops.environment.dto.request.UpdateTriggerRulesRequest;
import flowops.environment.dto.request.UpsertTriggerRuleRequest;
import flowops.environment.dto.response.TriggerRuleResponse;
import flowops.environment.repository.TriggerRuleRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.support.CronExpression;

/**
 * 환경별 자동 실행 트리거 규칙을 조회하고 일괄 갱신합니다.
 */
@Service
@RequiredArgsConstructor
public class TriggerRuleService {

    private final TriggerRuleRepository triggerRuleRepository;
    private final EnvironmentService environmentService;
    private final TriggerExecutionService triggerExecutionService;

    @Transactional(readOnly = true)
    public List<TriggerRuleResponse> listByEnvironment(Long environmentId) {
        environmentService.getEnvironment(environmentId);
        return triggerRuleRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId)
                .stream()
                .map(TriggerRuleResponse::from)
                .toList();
    }

    @Transactional
    public List<TriggerRuleResponse> updateRules(Long environmentId, UpdateTriggerRulesRequest request) {
        Environment environment = environmentService.getEnvironment(environmentId);
        List<TriggerRuleResponse> responses = new ArrayList<>();
        for (UpsertTriggerRuleRequest item : request.rules()) {
            validateTriggerRequest(item);
            TriggerRule rule = item.id() == null
                    ? TriggerRule.builder()
                            .environment(environment)
                            .triggerType(item.triggerType())
                            .scopeType(item.scopeType())
                            .scopeValue(item.scopeValue())
                            .triggerConfig(item.triggerConfig())
                            .executionMode(item.executionMode())
                            .enabled(Boolean.TRUE.equals(item.enabled()))
                            .build()
                    : triggerRuleRepository.findById(item.id())
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "트리거 규칙을 찾을 수 없습니다."));

            if (item.id() != null && !rule.getEnvironment().getId().equals(environmentId)) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "트리거 규칙이 요청한 환경에 속하지 않습니다.");
            }

            if (item.id() != null) {
                rule.update(
                        item.triggerType(),
                        item.scopeType(),
                        item.scopeValue(),
                        item.triggerConfig(),
                        item.executionMode(),
                        Boolean.TRUE.equals(item.enabled())
                );
            }

            responses.add(TriggerRuleResponse.from(triggerRuleRepository.save(rule)));
        }
        return responses;
    }

    private void validateTriggerRequest(UpsertTriggerRuleRequest request) {
        if (request.triggerType() != TriggerType.SCHEDULE) {
            return;
        }
        String cron = triggerExecutionService.resolveCronExpression(TriggerRule.builder()
                .environment(null)
                .triggerType(request.triggerType())
                .scopeType(request.scopeType())
                .scopeValue(request.scopeValue())
                .triggerConfig(request.triggerConfig())
                .executionMode(request.executionMode())
                .enabled(Boolean.TRUE.equals(request.enabled()))
                .build());
        if (cron == null || cron.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "SCHEDULE 트리거에는 cron 설정이 필요합니다.");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "유효하지 않은 cron 표현식입니다.");
        }
    }
}
