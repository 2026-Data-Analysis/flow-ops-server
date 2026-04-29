package flowops.coverage.controller;

import flowops.coverage.dto.response.ApiCoverageSummaryResponse;
import flowops.coverage.dto.response.ExecutionCoverageImpactResponse;
import flowops.coverage.service.CoverageService;
import flowops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 커버리지와 실행 영향도 조회 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Coverage", description = "커버리지 요약 및 실행 영향 조회 API")
public class CoverageController {

    private final CoverageService coverageService;

    @GetMapping("/apis/{apiId}/coverage")
    @Operation(summary = "API 커버리지 조회", description = "API별 테스트 케이스 수와 placeholder 커버리지 비율을 조회합니다.")
    public ApiResponse<ApiCoverageSummaryResponse> getApiCoverage(@PathVariable Long apiId) {
        return ApiResponse.success(coverageService.getApiCoverage(apiId));
    }

    @GetMapping("/executions/{executionId}/coverage-impact")
    @Operation(summary = "실행 커버리지 영향 조회", description = "실행 전후 커버리지 변화량을 placeholder 계산으로 조회합니다.")
    public ApiResponse<ExecutionCoverageImpactResponse> getExecutionCoverageImpact(@PathVariable Long executionId) {
        return ApiResponse.success(coverageService.getExecutionCoverageImpact(executionId));
    }
}
