package flowops.apiinventory.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.apiinventory.dto.request.SaveApiInventoryRequest;
import flowops.apiinventory.dto.response.ApiInventoryBranchSummaryResponse;
import flowops.apiinventory.dto.response.ApiInventoryDetailResponse;
import flowops.apiinventory.dto.response.ApiInventoryListResponse;
import flowops.apiinventory.dto.response.ApiInventoryResponse;
import flowops.apiinventory.dto.response.SampleTestCaseResponse;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.coverage.service.CoverageService;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.github.domain.entity.RepositoryInfo;
import flowops.github.service.GithubService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.project.domain.entity.Project;
import flowops.project.service.ProjectService;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API Inventory의 수동 저장, 목록 조회, 상세 조회를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class ApiInventoryService {

    private final ApiInventoryRepository apiInventoryRepository;
    private final ProjectService projectService;
    private final GithubService githubService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final TestCaseRepository testCaseRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final CoverageService coverageService;

    @Transactional
    public ApiInventoryResponse saveInventory(Long projectId, SaveApiInventoryRequest request) {
        Project project = projectService.getProject(projectId);
        RepositoryInfo repositoryInfo = request.repositoryId() == null
                ? null
                : githubService.getRepository(request.repositoryId());

        ApiInventory apiInventory = apiInventoryRepository.save(ApiInventory.builder()
                .project(project)
                .repositoryInfo(repositoryInfo)
                .method(request.method())
                .endpointPath(request.endpointPath())
                .operationId(request.operationId())
                .branchName(request.branchName())
                .summary(request.summary())
                .sourceType(request.sourceType())
                .status(ApiInventoryStatus.ACTIVE)
                .specVersion(request.specVersion())
                .authRequired(request.authRequired())
                .build());

        return toListItem(apiInventory);
    }

    @Transactional(readOnly = true)
    public ApiInventoryListResponse listInventories(
            Long projectId,
            Long repositoryId,
            String branchName,
            String domainTag,
            ApiHttpMethod method,
            TestLevel testLevel,
            ApiInventorySource sourceType,
            String keyword
    ) {
        projectService.getProject(projectId);
        RepositoryInfo repositoryInfo = repositoryId == null ? null : githubService.getRepository(repositoryId);
        String defaultBranchName = repositoryInfo == null ? branchName : repositoryInfo.getDefaultBranch();
        String effectiveBranchName = branchName;
        if ((effectiveBranchName == null || effectiveBranchName.isBlank()) && repositoryInfo != null) {
            effectiveBranchName = repositoryInfo.getDefaultBranch();
        }

        List<ApiInventory> inventories = apiInventoryRepository.findByFilters(
                projectId,
                repositoryId,
                blankToNull(effectiveBranchName),
                method,
                sourceType,
                blankToNull(keyword)
        ).stream()
                .filter(inventory -> matchesDomainTag(inventory, domainTag))
                .filter(inventory -> matchesTestLevel(inventory, testLevel))
                .toList();

        List<ApiInventoryBranchSummaryResponse> branchSummaries = inventories.stream()
                .collect(Collectors.groupingBy(inventory -> inventory.getBranchName() == null ? "UNKNOWN" : inventory.getBranchName()))
                .entrySet()
                .stream()
                .map(entry -> branchSummary(entry.getKey(), entry.getValue()))
                .toList();

        return new ApiInventoryListResponse(
                defaultBranchName,
                branchSummaries,
                inventories.stream().map(this::toListItem).toList()
        );
    }

    @Transactional(readOnly = true)
    public ApiInventoryDetailResponse getInventoryDetail(Long projectId, Long inventoryId) {
        ApiInventory apiInventory = apiInventoryRepository.findByIdAndProjectId(inventoryId, projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API 인벤토리를 찾을 수 없습니다."));
        Optional<ApiEndpoint> endpoint = findMatchingEndpoint(apiInventory);
        long totalTestCount = endpoint.map(value -> testCaseRepository.countByApiEndpointIdAndActiveTrue(value.getId())).orElse(0L);
        double coverage = endpoint.map(value -> coverageService.calculateCoveragePercent(value.getId())).orElse(0.0);
        List<TestLevel> testLevels = endpoint.map(value -> testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(value.getId())
                        .stream()
                        .map(TestCase::getTestLevel)
                        .distinct()
                        .toList())
                .orElse(List.of());
        double successRate = endpoint.map(value -> successRate(value.getId())).orElse(0.0);
        List<SampleTestCaseResponse> sampleTestCases = endpoint.map(value -> testCaseRepository.findTop3ByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(value.getId())
                        .stream()
                        .map(SampleTestCaseResponse::from)
                        .toList())
                .orElse(List.of());

        return ApiInventoryDetailResponse.from(
                apiInventory,
                testLevels,
                totalTestCount,
                coverage,
                successRate,
                sampleTestCases
        );
    }

    private ApiInventoryResponse toListItem(ApiInventory apiInventory) {
        Optional<ApiEndpoint> endpoint = findMatchingEndpoint(apiInventory);
        long totalTestCount = endpoint.map(value -> testCaseRepository.countByApiEndpointIdAndActiveTrue(value.getId())).orElse(0L);
        double coverage = endpoint.map(value -> coverageService.calculateCoveragePercent(value.getId())).orElse(0.0);
        List<TestLevel> testLevels = endpoint.map(value -> testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(value.getId())
                        .stream()
                        .map(TestCase::getTestLevel)
                        .distinct()
                        .toList())
                .orElse(List.of());

        return ApiInventoryResponse.from(apiInventory, testLevels, totalTestCount, coverage);
    }

    private ApiInventoryBranchSummaryResponse branchSummary(String branchName, List<ApiInventory> inventories) {
        List<ApiEndpoint> matchedEndpoints = inventories.stream()
                .map(this::findMatchingEndpoint)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        long totalTestCount = matchedEndpoints.stream()
                .mapToLong(endpoint -> testCaseRepository.countByApiEndpointIdAndActiveTrue(endpoint.getId()))
                .sum();
        double averageCoverage = matchedEndpoints.isEmpty()
                ? 0.0
                : matchedEndpoints.stream()
                .mapToDouble(endpoint -> coverageService.calculateCoveragePercent(endpoint.getId()))
                .average()
                .orElse(0.0);
        return new ApiInventoryBranchSummaryResponse(branchName, inventories.size(), averageCoverage, totalTestCount);
    }

    private Optional<ApiEndpoint> findMatchingEndpoint(ApiInventory apiInventory) {
        return toApiMethod(apiInventory.getMethod())
                .flatMap(method -> apiEndpointRepository.findFirstByMethodAndPath(method, apiInventory.getEndpointPath()));
    }

    private boolean matchesDomainTag(ApiInventory apiInventory, String domainTag) {
        String normalizedDomainTag = blankToNull(domainTag);
        if (normalizedDomainTag == null) {
            return true;
        }
        return findMatchingEndpoint(apiInventory)
                .map(ApiEndpoint::getDomainTag)
                .filter(tag -> tag != null && tag.equalsIgnoreCase(normalizedDomainTag))
                .isPresent();
    }

    private boolean matchesTestLevel(ApiInventory apiInventory, TestLevel testLevel) {
        if (testLevel == null) {
            return true;
        }
        return findMatchingEndpoint(apiInventory)
                .map(endpoint -> testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(endpoint.getId())
                        .stream()
                        .anyMatch(testCase -> testCase.getTestLevel() == testLevel))
                .orElse(false);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Optional<ApiMethod> toApiMethod(ApiHttpMethod method) {
        try {
            return Optional.of(ApiMethod.valueOf(method.name()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private double successRate(Long apiId) {
        long totalExecutions = executionStepLogRepository.countByTestCaseApiEndpointId(apiId);
        if (totalExecutions == 0) {
            return 0.0;
        }
        long successExecutions = executionStepLogRepository.countByTestCaseApiEndpointIdAndStatus(apiId, ExecutionStepStatus.SUCCESS);
        return Math.round((successExecutions * 1000.0 / totalExecutions)) / 10.0;
    }
}
