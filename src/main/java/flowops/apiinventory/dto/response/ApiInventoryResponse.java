package flowops.apiinventory.dto.response;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ApiInventoryResponse(
        @Schema(description = "API 인벤토리 ID", example = "100")
        Long id,
        @Schema(description = "프로젝트 ID", example = "1")
        Long projectId,
        @Schema(description = "저장소 ID", example = "20")
        Long repositoryId,
        @Schema(description = "HTTP 메서드", example = "GET")
        ApiHttpMethod method,
        @Schema(description = "엔드포인트 경로", example = "/orders/{orderId}")
        String endpointPath,
        @Schema(description = "오퍼레이션 ID", example = "getOrder")
        String operationId,
        @Schema(description = "브랜치명", example = "main")
        String branchName,
        @Schema(description = "요약", example = "주문 상세 조회")
        String summary,
        @Schema(description = "인벤토리 소스", example = "OPENAPI")
        ApiInventorySource sourceType,
        @Schema(description = "상태", example = "ACTIVE")
        ApiInventoryStatus status,
        @Schema(description = "자동 생성/수동 수정 상태", example = "AUTO")
        ApiInventoryEditStatus editStatus,
        @Schema(description = "스펙 버전", example = "3.0.1")
        String specVersion,
        @Schema(description = "인증 필요 여부", example = "true")
        boolean authRequired,
        @Schema(description = "연결된 테스트 위계 목록")
        List<TestLevel> testLevels,
        @Schema(description = "연결된 테스트 수", example = "8")
        long totalTestCount,
        @Schema(description = "커버리지 비율", example = "60.0")
        double coveragePercentage
) {
    public static ApiInventoryResponse from(
            ApiInventory apiInventory,
            List<TestLevel> testLevels,
            long totalTestCount,
            double coveragePercentage
    ) {
        return new ApiInventoryResponse(
                apiInventory.getId(),
                apiInventory.getProject().getId(),
                apiInventory.getRepositoryInfo() == null ? null : apiInventory.getRepositoryInfo().getId(),
                apiInventory.getMethod(),
                apiInventory.getEndpointPath(),
                apiInventory.getOperationId(),
                apiInventory.getBranchName(),
                apiInventory.getSummary(),
                apiInventory.getSourceType(),
                apiInventory.getStatus(),
                ApiInventoryEditStatus.from(apiInventory),
                apiInventory.getSpecVersion(),
                apiInventory.isAuthRequired(),
                testLevels,
                totalTestCount,
                coveragePercentage
        );
    }

}
