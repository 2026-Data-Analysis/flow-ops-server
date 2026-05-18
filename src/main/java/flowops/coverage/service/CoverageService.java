package flowops.coverage.service;

import flowops.coverage.dto.response.ApiCoverageSummaryResponse;
import flowops.coverage.dto.response.ExecutionCoverageImpactResponse;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.service.ExecutionQueryService;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.repository.TestCaseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API별 테스트 커버리지와 실행 후 커버리지 변화량을 계산합니다.
 */
@Service
public class CoverageService {

    private final TestCaseRepository testCaseRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final ObjectProvider<ExecutionQueryService> executionQueryServiceProvider;

    public CoverageService(
            TestCaseRepository testCaseRepository,
            ExecutionStepLogRepository executionStepLogRepository,
            ObjectProvider<ExecutionQueryService> executionQueryServiceProvider
    ) {
        this.testCaseRepository = testCaseRepository;
        this.executionStepLogRepository = executionStepLogRepository;
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
        ExecutionQueryService executionQueryService = executionQueryServiceProvider.getIfAvailable();
        if (executionQueryService == null) {
            return new ExecutionCoverageImpactResponse(executionId, 0.0, 0.0, 0.0);
        }
        executionQueryService.findExecution(executionId);
        List<Long> apiIds = executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId)
                .stream()
                .map(this::apiId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        double afterCoverage = apiIds.isEmpty()
                ? 0.0
                : apiIds.stream().mapToDouble(this::calculateCoveragePercent).average().orElse(0.0);
        double beforeCoverage = afterCoverage;
        return new ExecutionCoverageImpactResponse(executionId, beforeCoverage, afterCoverage, afterCoverage - beforeCoverage);
    }

    @Transactional(readOnly = true)
    public double calculateCoveragePercent(Long apiId) {
        List<TestCaseType> coveredTypes = testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(apiId)
                .stream()
                .map(TestCase::getType)
                .distinct()
                .toList();
        return Math.min(100.0, coveredTypes.size() * (100.0 / TestCaseType.values().length));
    }

    private Long apiId(ExecutionStepLog log) {
        if (log.getTestCase() != null) {
            return log.getTestCase().getApiEndpoint().getId();
        }
        if (log.getScenarioStep() != null) {
            return log.getScenarioStep().getApiEndpoint().getId();
        }
        return null;
    }
}
