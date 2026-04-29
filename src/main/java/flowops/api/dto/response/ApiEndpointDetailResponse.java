package flowops.api.dto.response;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record ApiEndpointDetailResponse(
        @Schema(description = "API ID", example = "10")
        Long id,
        @Schema(description = "앱 ID", example = "1")
        Long appId,
        @Schema(description = "HTTP 메서드", example = "POST")
        ApiMethod method,
        @Schema(description = "API 경로", example = "/payments")
        String path,
        @Schema(description = "도메인 태그", example = "PAYMENT")
        String domainTag,
        @Schema(description = "컨트롤러 이름", example = "PaymentController")
        String controllerName,
        @Schema(description = "요청 스키마", example = "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}")
        String requestSchema,
        @Schema(description = "응답 스키마", example = "{\"type\":\"object\",\"properties\":{\"paymentId\":{\"type\":\"string\"}}}")
        String responseSchema,
        @Schema(description = "Deprecated 여부", example = "false")
        boolean deprecated,
        @Schema(description = "연결된 테스트 케이스 수", example = "12")
        long testCaseCount,
        @Schema(description = "최근 실행 시각", example = "2026-04-12T02:00:00")
        LocalDateTime latestExecutionTime,
        @Schema(description = "커버리지 비율", example = "60.0")
        double coveragePercentage,
        @Schema(description = "생성 일시", example = "2026-04-10T11:00:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 일시", example = "2026-04-12T01:20:00")
        LocalDateTime updatedAt
) {

    public static ApiEndpointDetailResponse of(
            ApiEndpoint endpoint,
            long testCaseCount,
            LocalDateTime latestExecutionTime,
            double coveragePercentage
    ) {
        return new ApiEndpointDetailResponse(
                endpoint.getId(),
                endpoint.getApp().getId(),
                endpoint.getMethod(),
                endpoint.getPath(),
                endpoint.getDomainTag(),
                endpoint.getControllerName(),
                endpoint.getRequestSchema(),
                endpoint.getResponseSchema(),
                endpoint.isDeprecated(),
                testCaseCount,
                latestExecutionTime,
                coveragePercentage,
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt()
        );
    }
}
