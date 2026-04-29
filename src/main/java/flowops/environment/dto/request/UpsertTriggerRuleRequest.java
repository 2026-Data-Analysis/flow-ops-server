package flowops.environment.dto.request;

import flowops.environment.domain.entity.ExecutionMode;
import flowops.environment.domain.entity.TriggerScopeType;
import flowops.environment.domain.entity.TriggerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpsertTriggerRuleRequest(
        @Schema(description = "기존 규칙 ID. 신규 생성 시 null", example = "5")
        Long id,

        @Schema(description = "트리거 유형", example = "DEPLOY")
        @NotNull(message = "트리거 유형은 필수입니다.")
        TriggerType triggerType,

        @Schema(description = "적용 범위 유형", example = "BY_TAGS")
        @NotNull(message = "범위 유형은 필수입니다.")
        TriggerScopeType scopeType,

        @Schema(description = "적용 범위 값. SELECTED_APIS는 API ID 목록, BY_TAGS는 태그 목록을 JSON으로 저장합니다.", example = "[\"PAYMENT\"]")
        String scopeValue,

        @Schema(description = "트리거 상세 설정 JSON. SCHEDULE은 cron 표현식 등을 저장합니다.", example = "{\"cron\":\"0 0 * * * *\"}")
        String triggerConfig,

        @Schema(description = "실행 모드", example = "GENERATE_AND_RUN")
        @NotNull(message = "실행 모드는 필수입니다.")
        ExecutionMode executionMode,

        @Schema(description = "활성화 여부", example = "true")
        @NotNull(message = "활성화 여부는 필수입니다.")
        Boolean enabled
) {
}
