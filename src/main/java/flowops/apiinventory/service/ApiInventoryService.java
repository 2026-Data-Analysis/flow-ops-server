package flowops.apiinventory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.apiinventory.domain.DomainTag;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.apiinventory.dto.request.SaveApiInventoryRequest;
import flowops.apiinventory.dto.response.AgentApiInventorySearchResponse;
import flowops.apiinventory.dto.response.AgentApiInventorySearchResponse.AgentApiSpec;
import flowops.apiinventory.dto.response.AgentApiInventorySearchResponse.AgentTestCaseSpec;
import flowops.apiinventory.dto.response.ApiInventoryBranchSummaryResponse;
import flowops.apiinventory.dto.response.ApiInventoryDetailResponse;
import flowops.apiinventory.dto.response.ApiInventoryListResponse;
import flowops.apiinventory.dto.response.ApiInventoryResponse;
import flowops.apiinventory.repository.ApiInventoryRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final TestCaseRepository testCaseRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final ObjectMapper objectMapper;

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
                .domainTag(DomainTag.resolve(request.domainTag(), request.endpointPath()))
                .branchName(request.branchName())
                .summary(request.summary())
                .sourceType(request.sourceType())
                .status(ApiInventoryStatus.ACTIVE)
                .specVersion(request.specVersion())
                .authRequired(request.authRequired())
                .requestSchema(request.requestSchema())
                .responseSchema(request.responseSchema())
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

        String normalizedKeyword = blankToNull(keyword);
        List<ApiInventory> inventories = (normalizedKeyword == null
                ? apiInventoryRepository.findByFilters(
                        projectId,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType
                )
                : apiInventoryRepository.findByFiltersAndKeyword(
                        projectId,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType,
                        normalizedKeyword
                )).stream()
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
    public AgentApiInventorySearchResponse searchInventoriesForAgent(
            Long projectId,
            Long appId,
            Long repositoryId,
            String branchName,
            String domainTag,
            ApiHttpMethod method,
            TestLevel testLevel,
            ApiInventorySource sourceType,
            String keyword
    ) {
        List<ApiInventory> inventories = findInventories(
                projectId,
                repositoryId,
                branchName,
                domainTag,
                method,
                testLevel,
                sourceType,
                keyword
        );
        List<AgentApiSpec> apis = inventories.stream()
                .map(this::toAgentApiSpec)
                .toList();
        List<AgentTestCaseSpec> testCases = findTestCasesForAgent(appId, inventories).stream()
                .map(this::toAgentTestCaseSpec)
                .toList();

        return new AgentApiInventorySearchResponse(
                projectId,
                appId,
                repositoryId,
                blankToNull(branchName),
                blankToNull(keyword),
                inventories.size(),
                apis.size(),
                apis,
                testCases.size(),
                testCases
        );
    }

    @Transactional(readOnly = true)
    public ApiInventoryDetailResponse getInventoryDetail(Long projectId, Long inventoryId) {
        ApiInventory apiInventory = apiInventoryRepository.findByIdAndProjectId(inventoryId, projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API 인벤토리를 찾을 수 없습니다."));
        long totalTestCount = countTestCases(apiInventory);
        double coverage = Math.min(100.0, totalTestCount * 20.0);
        List<TestLevel> testLevels = findTestCases(apiInventory)
                .stream()
                .map(TestCase::getTestLevel)
                .distinct()
                .toList();
        double successRate = successRateByInventory(apiInventory.getId());

        return ApiInventoryDetailResponse.from(
                apiInventory,
                testLevels,
                totalTestCount,
                coverage,
                successRate
        );
    }

    private ApiInventoryResponse toListItem(ApiInventory apiInventory) {
        long totalTestCount = countTestCases(apiInventory);
        double coverage = Math.min(100.0, totalTestCount * 20.0);
        List<TestLevel> testLevels = findTestCases(apiInventory)
                .stream()
                .map(TestCase::getTestLevel)
                .distinct()
                .toList();

        return ApiInventoryResponse.from(
                apiInventory,
                testLevels,
                totalTestCount,
                coverage
        );
    }

    private List<ApiInventory> findInventories(
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
        if (repositoryId != null) {
            githubService.getRepository(repositoryId);
        }
        String effectiveBranchName = branchName;

        String normalizedKeyword = blankToNull(keyword);
        return (normalizedKeyword == null
                ? apiInventoryRepository.findByFilters(
                        projectId,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType
                )
                : apiInventoryRepository.findByFiltersAndKeyword(
                        projectId,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType,
                        normalizedKeyword
                )).stream()
                .filter(inventory -> matchesDomainTag(inventory, domainTag))
                .filter(inventory -> matchesTestLevel(inventory, testLevel))
                .toList();
    }

    private AgentApiSpec toAgentApiSpec(ApiInventory apiInventory) {
        return new AgentApiSpec(
                String.valueOf(apiInventory.getId()),
                apiInventory.getId(),
                apiInventory.getRepositoryInfo() == null ? null : apiInventory.getRepositoryInfo().getId(),
                apiInventory.getMethod().name(),
                apiInventory.getEndpointPath(),
                DomainTag.resolve(apiInventory.getDomainTag(), apiInventory.getEndpointPath()),
                apiInventory.getOperationId(),
                apiInventory.getSummary(),
                apiInventory.getBranchName(),
                parseJson(apiInventory.getRequestSchema()),
                parseJson(apiInventory.getResponseSchema()),
                apiInventory.isAuthRequired(),
                false
        );
    }

    private List<TestCase> findTestCasesForAgent(Long appId, List<ApiInventory> inventories) {
        if (appId != null) {
            return testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(appId);
        }
        Map<Long, TestCase> uniqueTestCases = new LinkedHashMap<>();
        inventories.stream()
                .flatMap(inventory -> findTestCases(inventory).stream())
                .forEach(testCase -> uniqueTestCases.putIfAbsent(testCase.getId(), testCase));
        return List.copyOf(uniqueTestCases.values());
    }

    private AgentTestCaseSpec toAgentTestCaseSpec(TestCase testCase) {
        Long apiId = testCase.getApiInventory() == null
                ? testCase.getApiEndpoint().getId()
                : testCase.getApiInventory().getId();
        return new AgentTestCaseSpec(
                String.valueOf(testCase.getId()),
                String.valueOf(apiId),
                testCase.getName(),
                testCase.getDescription(),
                testCase.getType() == null ? null : testCase.getType().name(),
                testCase.getTestLevel() == null ? null : testCase.getTestLevel().name(),
                testCase.getUserRole(),
                testCase.getStateCondition(),
                testCase.getDataVariant(),
                parseJson(testCase.getRequestSpec()),
                parseJson(testCase.getExpectedSpec()),
                parseJson(testCase.getAssertionSpec())
        );
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private ApiInventoryBranchSummaryResponse branchSummary(String branchName, List<ApiInventory> inventories) {
        long totalTestCount = inventories.stream()
                .mapToLong(this::countTestCases)
                .sum();
        double averageCoverage = inventories.isEmpty()
                ? 0.0
                : inventories.stream()
                .mapToDouble(inventory -> Math.min(100.0, countTestCases(inventory) * 20.0))
                .average()
                .orElse(0.0);
        return new ApiInventoryBranchSummaryResponse(branchName, inventories.size(), averageCoverage, totalTestCount);
    }

    private boolean matchesDomainTag(ApiInventory apiInventory, String domainTag) {
        String normalizedDomainTag = blankToNull(domainTag);
        if (normalizedDomainTag == null) {
            return true;
        }
        String resolvedDomainTag = DomainTag.resolve(apiInventory.getDomainTag(), apiInventory.getEndpointPath());
        return resolvedDomainTag != null && resolvedDomainTag.equalsIgnoreCase(normalizedDomainTag);
    }

    private boolean matchesTestLevel(ApiInventory apiInventory, TestLevel testLevel) {
        if (testLevel == null) {
            return true;
        }
        return findTestCases(apiInventory)
                .stream()
                .anyMatch(testCase -> testCase.getTestLevel() == testLevel);
    }

    private long countTestCases(ApiInventory apiInventory) {
        if (apiInventory.getRepositoryInfo() != null) {
            return testCaseRepository.countByRepositoryAndMethodAndPathAndActiveTrue(
                    apiInventory.getRepositoryInfo().getId(),
                    apiInventory.getMethod(),
                    apiInventory.getEndpointPath()
            );
        }
        return testCaseRepository.countByApiInventoryIdAndActiveTrue(apiInventory.getId());
    }

    private List<TestCase> findTestCases(ApiInventory apiInventory) {
        if (apiInventory.getRepositoryInfo() != null) {
            return testCaseRepository.findByRepositoryAndMethodAndPathAndActiveTrueOrderByUpdatedAtDesc(
                    apiInventory.getRepositoryInfo().getId(),
                    apiInventory.getMethod(),
                    apiInventory.getEndpointPath()
            );
        }
        return testCaseRepository.findByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(apiInventory.getId());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private double successRateByInventory(Long inventoryId) {
        long totalExecutions = executionStepLogRepository.countByTestCaseApiInventoryId(inventoryId);
        if (totalExecutions == 0) {
            return 0.0;
        }
        long successExecutions = executionStepLogRepository.countByTestCaseApiInventoryIdAndStatus(
                inventoryId,
                ExecutionStepStatus.SUCCESS
        );
        return Math.round((successExecutions * 1000.0 / totalExecutions)) / 10.0;
    }
}
