package flowops.environment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateTriggerRulesRequest(
        @Schema(description = "생성 또는 수정할 트리거 규칙 목록")
        @Valid
        @NotEmpty(message = "트리거 규칙은 하나 이상 필요합니다.")
        List<UpsertTriggerRuleRequest> rules
) {
}
