package flowops.testgeneration.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record SaveGeneratedDraftsRequest(
        @ArraySchema(schema = @Schema(description = "저장할 draft ID", example = "1001"))
        @NotEmpty(message = "저장할 초안을 하나 이상 선택해야 합니다.")
        List<Long> draftIds
) {
}
