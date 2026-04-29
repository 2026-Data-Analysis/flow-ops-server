package flowops.api.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ApiEndpointListItemResponse(
        @Schema(description = "API ID", example = "10")
        Long id,
        @Schema(description = "HTTP 메서드", example = "POST")
        ApiMethod method,
        @Schema(description = "API 경로", example = "/payments")
        String path,
        @Schema(description = "도메인 태그", example = "PAYMENT")
        String domainTag,
        @Schema(description = "컨트롤러 이름", example = "PaymentController")
        String controllerName,
        @Schema(description = "Deprecated 여부", example = "false")
        boolean deprecated,
        @Schema(description = "연결된 테스트 케이스 수", example = "12")
        long testCaseCount,
        @Schema(description = "최근 실행 시각", example = "2026-04-12T02:00:00")
        LocalDateTime latestExecutionTime,
        @Schema(description = "커버리지 비율", example = "60.0")
        double coveragePercentage
) {

    public static ApiEndpointListItemResponse of(
            ApiEndpoint endpoint,
            long testCaseCount,
            LocalDateTime latestExecutionTime,
            double coveragePercentage
    ) {
        return new ApiEndpointListItemResponse(
                endpoint.getId(),
                endpoint.getMethod(),
                endpoint.getPath(),
                endpoint.getDomainTag(),
                endpoint.getControllerName(),
                endpoint.isDeprecated(),
                testCaseCount,
                latestExecutionTime,
                coveragePercentage
        );
    }
}
