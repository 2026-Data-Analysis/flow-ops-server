package flowops.api.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.dto.response.ApiEndpointDetailResponse;
import flowops.api.dto.response.ApiEndpointListItemResponse;
import flowops.api.repository.ApiEndpointRepository;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.app.domain.entity.App;
import flowops.coverage.service.CoverageService;
import flowops.execution.repository.ExecutionRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.global.response.PageResponse;
import flowops.testcase.repository.TestCaseRepository;
import java.util.List;
import java.util.Optional;
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
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API 엔드포인트를 찾을 수 없습니다. apiId=" + apiId));
    }

    @Transactional(readOnly = true)
    public List<ApiEndpoint> getApiEndpointsByDomain(Long appId, String domainTag) {
        if (appId == null || domainTag == null || domainTag.isBlank()) {
            return List.of();
        }
        return apiEndpointRepository.findByAppIdAndDomainTagOrderByPathAscMethodAsc(appId, domainTag);
    }

    @Transactional(readOnly = true)
    public ApiEndpoint findFirstByMethodAndPath(ApiMethod method, String path) {
        return apiEndpointRepository.findFirstByMethodAndPath(method, path)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "API 엔드포인트를 찾을 수 없습니다. method=%s, path=%s".formatted(method, path)
                ));
    }

    @Transactional(readOnly = true)
    public ApiEndpoint findFirstByAppIdAndMethodAndPath(Long appId, ApiMethod method, String path) {
        return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(appId, method, path)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "API 엔드포인트를 찾을 수 없습니다. appId=%s, method=%s, path=%s".formatted(appId, method, path)
                ));
    }

    @Transactional(readOnly = true)
    public Optional<ApiEndpoint> findCleanupEndpoint(ApiEndpoint createdEndpoint) {
        if (createdEndpoint == null || createdEndpoint.getApp() == null || createdEndpoint.getApp().getId() == null) {
            return Optional.empty();
        }
        String basePath = normalizePath(createdEndpoint.getPath());
        List<ApiEndpoint> deleteEndpoints = apiEndpointRepository.findByAppIdAndMethod(
                createdEndpoint.getApp().getId(),
                ApiMethod.DELETE
        );
        return deleteEndpoints.stream()
                .filter(candidate -> cleanupPathMatches(basePath, candidate.getPath()))
                .findFirst();
    }

    @Transactional
    public ApiEndpoint findOrCreateFromInventory(App app, ApiInventory inventory) {
        ApiMethod method = ApiMethod.valueOf(inventory.getMethod().name());
        return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(app.getId(), method, inventory.getEndpointPath())
                .orElseGet(() -> apiEndpointRepository.save(ApiEndpoint.builder()
                        .app(app)
                        .method(method)
                        .path(inventory.getEndpointPath())
                        .domainTag(inventory.getDomainTag())
                        .controllerName(inventory.getOperationId())
                        .requestSchema(inventory.getRequestSchema())
                        .responseSchema(inventory.getResponseSchema())
                        .deprecated(false)
                        .build()));
    }

    private boolean cleanupPathMatches(String basePath, String deletePath) {
        String normalizedDeletePath = normalizePath(deletePath);
        return normalizedDeletePath.startsWith(basePath + "/{")
                || normalizedDeletePath.startsWith(basePath + "/:")
                || removePathParams(normalizedDeletePath).equals(basePath);
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String removePathParams(String path) {
        return path.replaceAll("/\\{[^}/]+}", "").replaceAll("/:[A-Za-z][A-Za-z0-9_]*", "");
    }
}
