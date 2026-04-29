package flowops.aiintegration.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeSpecRequest(
        @Schema(description = "스펙 참조 경로", example = "s3://specs/order/openapi.yaml")
        @NotBlank @Size(max = 255) String sourceReference,
        @Schema(description = "분석할 스펙 원문", example = "openapi: 3.0.1")
        @NotBlank String specContent
) {
}
