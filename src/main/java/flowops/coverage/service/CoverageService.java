package flowops.coverage.service;

import flowops.coverage.dto.response.ApiCoverageSummaryResponse;
import flowops.coverage.dto.response.ExecutionCoverageImpactResponse;
import flowops.execution.service.ExecutionQueryService;
import flowops.testcase.repository.TestCaseRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API별 테스트 커버리지와 실행 후 커버리지 변화량을 계산합니다.
 */
@Service
public class CoverageService {

    private final TestCaseRepository testCaseRepository;
    private final ObjectProvider<ExecutionQueryService> executionQueryServiceProvider;

    public CoverageService(TestCaseRepository testCaseRepository, ObjectProvider<ExecutionQueryService> executionQueryServiceProvider) {
        this.testCaseRepository = testCaseRepository;
        this.executionQueryServiceProvider = executionQueryServiceProvider;
    }

    @Transactional(readOnly = true)
    public ApiCoverageSummaryResponse getApiCoverage(Long apiId) {
        long totalTestCases = testCaseRepository.countByApiEndpointIdAndActiveTrue(apiId);
        return new ApiCoverageSummaryResponse(
                apiId,
                totalTestCases,
                calculateCoveragePercent(apiId),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public ExecutionCoverageImpactResponse getExecutionCoverageImpact(Long executionId) {
        double beforeCoverage = 35.0;
        double afterCoverage = beforeCoverage;
        ExecutionQueryService executionQueryService = executionQueryServiceProvider.getIfAvailable();
        if (executionQueryService != null) {
            var execution = executionQueryService.findExecution(executionId);
            afterCoverage = Math.min(100.0, beforeCoverage + (execution.getPassedCount() * 2.5));
        }
        return new ExecutionCoverageImpactResponse(executionId, beforeCoverage, afterCoverage, afterCoverage - beforeCoverage);
    }

    @Transactional(readOnly = true)
    public double calculateCoveragePercent(Long apiId) {
        return Math.min(100.0, testCaseRepository.countByApiEndpointIdAndActiveTrue(apiId) * 20.0);
    }
}
