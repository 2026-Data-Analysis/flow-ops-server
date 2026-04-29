package flowops.apiinventory.dto.response;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record ScanResultResponse(
        @Schema(description = "스캔한 브랜치 이름", example = "main")
        String branchName,
        @Schema(description = "감지된 API 엔드포인트 수", example = "24")
        int detectedEndpointCount,
        @Schema(description = "API 명세 도구명", example = "OpenAPI")
        String specToolName,
        @Schema(description = "API 명세 도구 버전", example = "3.0.1")
        String specToolVersion,
        @Schema(description = "HTTP 메서드별 엔드포인트 수")
        Map<ApiHttpMethod, Integer> methodEndpointCounts,
        @Schema(description = "감지된 프레임워크 이름", example = "Spring Boot")
        String frameworkName,
        @Schema(description = "감지된 프레임워크 버전", example = "3.4.7")
        String frameworkVersion,
        @Schema(description = "감지된 REST Controller 수", example = "8")
        Integer restControllerCount
) {
}
