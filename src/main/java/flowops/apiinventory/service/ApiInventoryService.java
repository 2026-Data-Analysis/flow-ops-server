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
import flowops.apiinventory.dto.response.AgentApiInventorySearchResponse.AgentNaturalLanguageScenarioRequest;
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
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioAuthPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioExistingTestCasePayload;
import flowops.project.domain.entity.Project;
import flowops.project.service.ProjectService;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
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
                        null,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType
                )
                : apiInventoryRepository.findByFiltersAndKeyword(
                        projectId,
                        null,
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
                appId,
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
        List<TestCase> agentTestCases = findTestCasesForAgent(appId, inventories);
        List<AgentTestCaseSpec> testCases = agentTestCases.stream()
                .map(this::toAgentTestCaseSpec)
                .toList();
        List<Scenario> agentScenarios = findScenariosForAgent(appId);
        List<ScenarioSummaryResponse> scenarios = agentScenarios.stream()
                .map(scenario -> ScenarioSummaryResponse.from(
                        scenario,
                        scenarioStepRepository.countByScenarioId(scenario.getId())
                ))
                .toList();
        AgentNaturalLanguageScenarioRequest naturalLanguageScenarioRequest = naturalLanguageScenarioRequest(
                projectId,
                inventories,
                agentTestCases,
                agentScenarios
        );

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
                testCases,
                scenarios.size(),
                scenarios,
                naturalLanguageScenarioRequest
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
            Long appId,
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
                        appId,
                        repositoryId,
                        blankToNull(effectiveBranchName),
                        method,
                        sourceType
                )
                : apiInventoryRepository.findByFiltersAndKeyword(
                        projectId,
                        appId,
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
        JsonNode requestSchema = parseJson(apiInventory.getRequestSchema());
        JsonNode responseSchema = parseJson(apiInventory.getResponseSchema());
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
                requestSchema,
                responseSchema,
                integerArray(responseSchema, "expectedStatusCodes"),
                integerArray(responseSchema, "errorStatusCodes"),
                errorCodes(responseSchema),
                apiInventory.isAuthRequired(),
                false
        );
    }

    private AgentNaturalLanguageScenarioRequest naturalLanguageScenarioRequest(
            Long projectId,
            List<ApiInventory> inventories,
            List<TestCase> testCases,
            List<Scenario> scenarios
    ) {
        String scenarioProjectId = scenarioProjectId(projectId);
        return new AgentNaturalLanguageScenarioRequest(
                "NATURAL_LANGUAGE",
                null,
                new ScenarioApiInventoryPayload(
                        scenarioProjectId,
                        inventories.stream().map(this::toScenarioEndpointPayload).toList()
                ),
                null,
                testCases.stream().map(this::toScenarioExistingTestCasePayload).toList(),
                scenarios.stream().map(this::toExistingScenarioSummary).toList(),
                2,
                5
        );
    }

    private ScenarioEndpointPayload toScenarioEndpointPayload(ApiInventory apiInventory) {
        JsonNode requestSchema = parseJson(apiInventory.getRequestSchema());
        JsonNode responseSchema = parseJson(apiInventory.getResponseSchema());
        String domainTag = DomainTag.resolve(apiInventory.getDomainTag(), apiInventory.getEndpointPath());
        return new ScenarioEndpointPayload(
                endpointId(apiInventory.getMethod().name(), apiInventory.getEndpointPath()),
                apiInventory.getEndpointPath(),
                apiInventory.getMethod().name(),
                apiInventory.getSummary(),
                apiInventory.getSummary(),
                parameters(requestSchema),
                authPayload(apiInventory.isAuthRequired()),
                requestBodySchema(requestSchema),
                responseSchema,
                integerArray(responseSchema, "expectedStatusCodes"),
                integerArray(responseSchema, "errorStatusCodes"),
                errorCodes(responseSchema),
                tags(domainTag)
        );
    }

    private ScenarioExistingTestCasePayload toScenarioExistingTestCasePayload(TestCase testCase) {
        String testLevel = testCase.getTestLevel() == null ? null : testCase.getTestLevel().name();
        return new ScenarioExistingTestCasePayload(
                String.valueOf(testCase.getId()),
                testCaseApiId(testCase),
                testCase.getName(),
                testCase.getType() == null ? null : testCase.getType().name(),
                testCase.getDescription(),
                testLevel,
                testLevel,
                parseJson(testCase.getRequestSpec()),
                parseJson(testCase.getExpectedSpec()),
                parseJson(testCase.getAssertionSpec()),
                extractExpectedStatus(testCase.getExpectedSpec())
        );
    }

    private ExistingScenarioSummary toExistingScenarioSummary(Scenario scenario) {
        List<String> stepApiIds = scenarioStepRepository
                .findByScenarioIdOrderByStepOrderAsc(scenario.getId())
                .stream()
                .map(this::scenarioStepEndpointId)
                .filter(Objects::nonNull)
                .toList();
        return new ExistingScenarioSummary(scenario.getName(), stepApiIds);
    }

    private String scenarioStepEndpointId(ScenarioStep step) {
        if (step.getApiInventory() != null) {
            return endpointId(step.getApiInventory().getMethod().name(), step.getApiInventory().getEndpointPath());
        }
        if (step.getApiEndpoint() != null) {
            return endpointId(step.getApiEndpoint().getMethod().name(), step.getApiEndpoint().getPath());
        }
        return null;
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

    private List<Scenario> findScenariosForAgent(Long appId) {
        if (appId == null) {
            return List.of();
        }
        return scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId);
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

    private String scenarioProjectId(Long projectId) {
        return projectId == null ? null : "project-" + projectId;
    }

    private String endpointId(String method, String path) {
        return method + ":" + path;
    }

    private String testCaseApiId(TestCase testCase) {
        if (testCase.getApiInventory() != null) {
            return endpointId(testCase.getApiInventory().getMethod().name(), testCase.getApiInventory().getEndpointPath());
        }
        return endpointId(testCase.getApiEndpoint().getMethod().name(), testCase.getApiEndpoint().getPath());
    }

    private ScenarioAuthPayload authPayload(boolean authRequired) {
        return authRequired
                ? new ScenarioAuthPayload("bearer", "header")
                : new ScenarioAuthPayload("none", null);
    }

    private JsonNode requestBodySchema(JsonNode requestSchema) {
        if (requestSchema == null || requestSchema.isNull() || requestSchema.isMissingNode()) {
            return objectMapper.nullNode();
        }
        if (requestSchema.isObject() && requestSchema.has("body")) {
            return requestSchema.get("body");
        }
        return requestSchema;
    }

    private JsonNode parameters(JsonNode requestSchema) {
        var parameters = objectMapper.createArrayNode();
        addParameters(parameters, requestSchema, "pathParams", "path");
        addParameters(parameters, requestSchema, "queryParams", "query");
        addParameters(parameters, requestSchema, "headers", "header");
        return parameters;
    }

    private void addParameters(com.fasterxml.jackson.databind.node.ArrayNode target, JsonNode requestSchema, String sourceField, String location) {
        if (requestSchema == null || !requestSchema.has(sourceField) || !requestSchema.get(sourceField).isObject()) {
            return;
        }
        requestSchema.get(sourceField).fields().forEachRemaining(entry -> {
            var parameter = objectMapper.createObjectNode();
            parameter.put("name", entry.getKey());
            parameter.put("in", location);
            parameter.set("schema", entry.getValue());
            target.add(parameter);
        });
    }

    private List<String> tags(String domainTag) {
        return domainTag == null || domainTag.isBlank() ? List.of() : List.of(domainTag);
    }

    private Integer extractExpectedStatus(String expectedSpec) {
        if (expectedSpec == null || expectedSpec.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(expectedSpec);
            JsonNode statusCode = node.path("statusCode");
            if (!statusCode.isMissingNode() && statusCode.canConvertToInt()) {
                return statusCode.intValue();
            }
            JsonNode status = node.path("status");
            if (!status.isMissingNode() && status.canConvertToInt()) {
                return status.intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
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

    private List<Integer> integerArray(JsonNode root, String fieldName) {
        if (root == null || !root.has(fieldName) || !root.get(fieldName).isArray()) {
            return List.of();
        }
        List<Integer> values = new java.util.ArrayList<>();
        root.get(fieldName).forEach(value -> {
            if (value.canConvertToInt()) {
                values.add(value.intValue());
            }
        });
        return values;
    }

    private List<String> errorCodes(JsonNode responseSchema) {
        if (responseSchema == null || !responseSchema.has("responses") || !responseSchema.get("responses").isArray()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        responseSchema.get("responses").forEach(response -> {
            JsonNode sampleBody = response.path("sampleBody");
            addTextField(codes, sampleBody, "errorCode");
            addTextField(codes, sampleBody, "code");
        });
        return List.copyOf(codes);
    }

    private void addTextField(LinkedHashSet<String> values, JsonNode node, String fieldName) {
        if (node != null && node.hasNonNull(fieldName) && node.get(fieldName).isTextual()) {
            values.add(node.get(fieldName).asText());
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
