package flowops.apiinventory.dto.request;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveApiInventoryRequest(
        @Schema(description = "저장소 ID", example = "20")
        Long repositoryId,
        @Schema(description = "HTTP 메서드", example = "GET")
        @NotNull ApiHttpMethod method,
        @Schema(description = "엔드포인트 경로", example = "/orders/{orderId}")
        @NotBlank @Size(max = 500) String endpointPath,
        @Schema(description = "오퍼레이션 ID", example = "getOrder")
        @Size(max = 150) String operationId,
        @Schema(description = "브랜치명", example = "main")
        @Size(max = 100) String branchName,
        @Schema(description = "요약", example = "주문 상세 조회")
        @Size(max = 500) String summary,
        @Schema(description = "인벤토리 소스", example = "OPENAPI")
        @NotNull ApiInventorySource sourceType,
        @Schema(description = "스펙 버전", example = "3.0.1")
        @Size(max = 50) String specVersion,
        @Schema(description = "인증 필요 여부", example = "true")
        boolean authRequired
) {
}
