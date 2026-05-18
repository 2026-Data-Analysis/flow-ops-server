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
@Tag(name = "커버리지", description = "커버리지 요약 및 실행 영향 조회 API")
public class CoverageController {

    private final CoverageService coverageService;

    @GetMapping("/apis/{apiId}/coverage")
    @Operation(summary = "API 커버리지 조회", description = "API별 활성 테스트 케이스 유형 기준 커버리지 비율을 조회합니다.")
    public ApiResponse<ApiCoverageSummaryResponse> getApiCoverage(@PathVariable Long apiId) {
        return ApiResponse.success(coverageService.getApiCoverage(apiId));
    }

    @GetMapping("/executions/{executionId}/coverage-impact")
    @Operation(summary = "실행 커버리지 영향 조회", description = "실행 로그에 포함된 API들의 현재 커버리지 값을 기반으로 실행 영향을 조회합니다.")
    public ApiResponse<ExecutionCoverageImpactResponse> getExecutionCoverageImpact(@PathVariable Long executionId) {
        return ApiResponse.success(coverageService.getExecutionCoverageImpact(executionId));
    }
}
