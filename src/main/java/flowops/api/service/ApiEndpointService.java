package flowops.api.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.dto.response.ApiEndpointDetailResponse;
import flowops.api.dto.response.ApiEndpointListItemResponse;
import flowops.api.repository.ApiEndpointRepository;
import flowops.coverage.service.CoverageService;
import flowops.execution.repository.ExecutionRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.global.response.PageResponse;
import flowops.testcase.repository.TestCaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집된 API 엔드포인트를 조회하고 커버리지/실행 정보를 함께 조합합니다.
 */
@Service
public class ApiEndpointService {

    private final ApiEndpointRepository apiEndpointRepository;
    private final TestCaseRepository testCaseRepository;
    private final ExecutionRepository executionRepository;
    private final CoverageService coverageService;

    public ApiEndpointService(
            ApiEndpointRepository apiEndpointRepository,
            TestCaseRepository testCaseRepository,
            ExecutionRepository executionRepository,
            CoverageService coverageService
    ) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.testCaseRepository = testCaseRepository;
        this.executionRepository = executionRepository;
        this.coverageService = coverageService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiEndpointListItemResponse> getApiEndpoints(
            Long appId,
            String domainTag,
            ApiMethod method,
            Pageable pageable
    ) {
        Page<ApiEndpointListItemResponse> page = apiEndpointRepository.findByFilters(appId, domainTag, method, pageable)
                .map(endpoint -> ApiEndpointListItemResponse.of(
                        endpoint,
                        testCaseRepository.countByApiEndpointIdAndActiveTrue(endpoint.getId()),
                        executionRepository.findLatestEndedAtByApiEndpointId(endpoint.getId()).orElse(null),
                        coverageService.calculateCoveragePercent(endpoint.getId())
                ));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ApiEndpointDetailResponse getApiEndpointDetail(Long apiId) {
        ApiEndpoint endpoint = getApiEndpoint(apiId);
        return ApiEndpointDetailResponse.of(
                endpoint,
                testCaseRepository.countByApiEndpointIdAndActiveTrue(apiId),
                executionRepository.findLatestEndedAtByApiEndpointId(apiId).orElse(null),
                coverageService.calculateCoveragePercent(apiId)
        );
    }

    @Transactional(readOnly = true)
    public ApiEndpoint getApiEndpoint(Long apiId) {
        return apiEndpointRepository.findById(apiId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API 엔드포인트를 찾을 수 없습니다."));
    }
    @Transactional(readOnly = true)
    public ApiEndpoint findFirstByMethodAndPath(ApiMethod method, String path) {
        return apiEndpointRepository.findFirstByMethodAndPath(method, path)
    }
}
