package flowops.environment.dto.response;

import flowops.environment.domain.entity.ExecutionMode;
import flowops.environment.domain.entity.TriggerRule;
import flowops.environment.domain.entity.TriggerScopeType;
import flowops.environment.domain.entity.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record TriggerRuleResponse(
        @Schema(description = "트리거 규칙 ID", example = "8")
        Long id,
        @Schema(description = "환경 ID", example = "3")
        Long environmentId,
        @Schema(description = "트리거 유형", example = "DEPLOY")
        TriggerType triggerType,
        @Schema(description = "적용 범위 유형", example = "BY_TAGS")
        TriggerScopeType scopeType,
        @Schema(description = "적용 범위 값", example = "[\"PAYMENT\"]")
        String scopeValue,
        @Schema(description = "트리거 상세 설정 JSON", example = "{\"cron\":\"0 0 * * * *\"}")
        String triggerConfig,
        @Schema(description = "실행 모드", example = "RUN_EXISTING")
        ExecutionMode executionMode,
        @Schema(description = "활성 여부", example = "true")
        boolean enabled,
        @Schema(description = "마지막 자동 실행 시각", example = "2026-04-29T20:20:00")
        LocalDateTime lastTriggeredAt,
        @Schema(description = "생성 일시", example = "2026-04-12T01:05:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T01:15:00")
        LocalDateTime updatedAt
) {

    public static TriggerRuleResponse from(TriggerRule rule) {
        return new TriggerRuleResponse(
                rule.getId(),
                rule.getEnvironment().getId(),
                rule.getTriggerType(),
                rule.getScopeType(),
                rule.getScopeValue(),
                rule.getTriggerConfig(),
                rule.getExecutionMode(),
                rule.isEnabled(),
                rule.getLastTriggeredAt(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
