package flowops.testgeneration.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.service.EnvironmentService;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiGeneratedDraftCommand;
import flowops.integration.ai.AiTestGenerationGateway;
import flowops.execution.dto.response.GenerateFailureTestCasesResponse;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationApiSelection;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import flowops.testgeneration.dto.request.CreateTestGenerationRequest;
import flowops.testgeneration.dto.request.SaveGeneratedDraftsRequest;
import flowops.testgeneration.dto.request.SaveGeneratedDraftsRequest.TestCaseDraftSaveRequest;
import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import flowops.testgeneration.dto.response.SaveGeneratedDraftsResponse;
import flowops.testgeneration.dto.response.TestGenerationDetailResponse;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 기반 테스트 생성 요청을 접수하고 생성된 초안을 테스트케이스로 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class TestGenerationService {

    private final TestGenerationRepository testGenerationRepository;
    private final TestGenerationApiSelectionRepository selectionRepository;
    private final GeneratedTestCaseDraftRepository draftRepository;
    private final AppService appService;
    private final EnvironmentService environmentService;
    private final ApiEndpointService apiEndpointService;
    private final ApiInventoryRepository apiInventoryRepository;
    private final AiTestGenerationGateway aiTestGenerationGateway;
    private final TestCaseRepository testCaseRepository;

    @Transactional
    public TestGenerationDetailResponse requestGeneration(CreateTestGenerationRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = request.environmentId() == null ? null : environmentService.getEnvironment(request.environmentId());
        if (environment != null && !environment.getApp().getId().equals(app.getId())) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "테스트 생성 환경이 요청한 앱에 속하지 않습니다.");
        }
        TestGeneration generation = testGenerationRepository.save(TestGeneration.builder()
                .app(app)
                .environment(environment)
                .status(TestGenerationStatus.REQUESTED)
                .requestedBy(request.requestedBy())
                .contextSummary(request.contextSummary())
                .currentCoverage(toBigDecimal(request.currentCoverage()))
                .predictedCoverage(toBigDecimal(request.currentCoverage()))
                .existingCount(0)
                .newCount(0)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build());

        List<TestGenerationApiSelection> selections = request.selectedApiIds().stream()
                .map(apiId -> {
                    ApiInventory apiInventory = apiInventoryRepository.findById(apiId).orElse(null);
                    validateInventoryEnvironment(apiInventory, environment);
                    ApiEndpoint apiEndpoint = apiInventory == null ? apiEndpointService.getApiEndpoint(apiId) : endpointForInventory(apiInventory);
                    return TestGenerationApiSelection.builder()
                            .generation(generation)
                            .apiEndpoint(apiEndpoint)
                            .apiInventory(apiInventory)
                            .build();
                })
                .map(selectionRepository::save)
                .toList();

        generateDraftsAsync(generation.getId(), request.selectedApiIds());
        return TestGenerationDetailResponse.of(generation, selections);
    }

    @Transactional(readOnly = true)
    public TestGenerationDetailResponse getDetail(Long generationId) {
        TestGeneration generation = getGeneration(generationId);
        return TestGenerationDetailResponse.of(generation, selectionRepository.findByGenerationId(generationId));
    }

    @Transactional(readOnly = true)
    public List<GeneratedTestCaseDraftResponse> getDrafts(Long generationId) {
        getGeneration(generationId);
        return draftRepository.findByGenerationIdOrderByCreatedAtAsc(generationId)
                .stream()
                .map(GeneratedTestCaseDraftResponse::from)
                .toList();
    }

    @Transactional
    public SaveGeneratedDraftsResponse saveDrafts(Long generationId, SaveGeneratedDraftsRequest request) {
        TestGeneration generation = getGeneration(generationId);
        List<Long> draftIds = request.testCases().stream()
                .map(TestCaseDraftSaveRequest::draftId)
                .toList();
        Map<Long, TestCaseDraftSaveRequest> saveRequests = new HashMap<>();
        for (TestCaseDraftSaveRequest testCaseRequest : request.testCases()) {
            if (saveRequests.put(testCaseRequest.draftId(), testCaseRequest) != null) {
                throw new flowops.global.exception.ApiException(
                        flowops.global.response.ErrorCode.INVALID_INPUT,
                        "같은 초안을 중복으로 저장할 수 없습니다."
                );
            }
        }

        List<GeneratedTestCaseDraft> drafts = draftRepository.findByGenerationIdAndIdIn(generationId, draftIds);
        if (drafts.size() != draftIds.size()) {
            throw new flowops.global.exception.ApiException(
                    flowops.global.response.ErrorCode.INVALID_INPUT,
                    "저장할 수 없는 초안이 포함되어 있습니다."
            );
        }

        List<Long> savedTestCaseIds = new ArrayList<>();
        LinkedHashSet<Long> apiIds = new LinkedHashSet<>();
        for (GeneratedTestCaseDraft draft : drafts) {
            TestCaseDraftSaveRequest saveRequest = saveRequests.get(draft.getId());
            draft.selectForSave();
            TestCase savedTestCase = testCaseRepository.save(TestCase.builder()
                    .app(generation.getApp())
                    .apiEndpoint(draft.getApiEndpoint())
                    .apiInventory(draft.getApiInventory())
                    .name(saveRequest.name().trim())
                    .description(defaultIfBlank(saveRequest.description(), draft.getDescription()))
                    .type(parseType(defaultIfBlank(saveRequest.type(), draft.getType())))
                    .testLevel(resolveTestLevel(saveRequest.testLevel(), generation))
                    .source(isEdited(draft, saveRequest) ? TestCaseSource.EDITED : TestCaseSource.AUTO)
                    .userRole(defaultIfBlank(saveRequest.userRole(), draft.getUserRole()))
                    .stateCondition(defaultIfBlank(saveRequest.stateCondition(), draft.getStateCondition()))
                    .dataVariant(defaultIfBlank(saveRequest.dataVariant(), draft.getDataVariant()))
                    .requestSpec(defaultIfBlank(saveRequest.requestSpec(), draft.getRequestSpec()))
                    .expectedSpec(saveRequest.expectedResult().trim())
                    .assertionSpec(defaultIfBlank(saveRequest.assertionSpec(), saveRequest.expectedResult()))
                    .active(true)
                    .version(1)
                    .build());
            savedTestCaseIds.add(savedTestCase.getId());
            apiIds.add(savedTestCase.getApiEndpoint().getId());
        }
        return new SaveGeneratedDraftsResponse(generationId, savedTestCaseIds.size(), savedTestCaseIds, List.copyOf(apiIds));
    }

    @Transactional
    public GenerateFailureTestCasesResponse generateFromFailure(
            Execution execution,
            ExecutionStepLog failedLog,
            String requestedBy,
            Double currentCoverage
    ) {
        TestGeneration generation = testGenerationRepository.save(TestGeneration.builder()
                .app(execution.getApp())
                .environment(execution.getEnvironment())
                .status(TestGenerationStatus.COMPLETED)
                .requestedBy(requestedBy)
                .contextSummary(buildFailureContextSummary(execution, failedLog))
                .currentCoverage(toBigDecimal(currentCoverage))
                .predictedCoverage(toPredictedCoverage(currentCoverage))
                .existingCount(0)
                .newCount(2)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build());

        selectionRepository.save(TestGenerationApiSelection.builder()
                .generation(generation)
                .apiEndpoint(resolveApiEndpoint(failedLog))
                .build());

        List<GeneratedTestCaseDraft> drafts = List.of(
                createFailureDraft(generation, failedLog, false),
                createFailureRegressionDraft(generation, failedLog)
        );

        return new GenerateFailureTestCasesResponse(
                generation.getId(),
                execution.getId(),
                failedLog.getId(),
                resolveApiEndpoint(failedLog).getId(),
                failedLog.getErrorMessage(),
                expectedBehavior(failedLog),
                actualBehavior(failedLog),
                drafts.stream().map(GeneratedTestCaseDraftResponse::from).toList()
        );
    }

    @Async("applicationTaskExecutor")
    @Transactional
    public void generateDraftsAsync(Long generationId, List<Long> apiIds) {
        // 사용자 요청 응답을 막지 않도록 초안 생성은 비동기로 처리합니다.
        TestGeneration generation = getGeneration(generationId);
        try {
            generation.markProcessing();
            List<AiGeneratedDraftCommand> commands = aiTestGenerationGateway.generateDrafts(generation, apiIds);
            int duplicateCount = 0;
            int newCount = 0;
            for (AiGeneratedDraftCommand command : commands) {
                ApiEndpoint apiEndpoint = apiEndpointService.getApiEndpoint(command.apiId());
                ApiInventory apiInventory = apiInventoryRepository.findById(command.apiId()).orElse(null);
                validateInventoryEnvironment(apiInventory, generation.getEnvironment());
                if (apiInventory != null) {
                    apiEndpoint = endpointForInventory(apiInventory);
                }
                draftRepository.save(GeneratedTestCaseDraft.builder()
                        .generation(generation)
                        .apiEndpoint(apiEndpoint)
                        .apiInventory(apiInventory)
                        .title(command.title())
                        .description(command.description())
                        .type(command.type())
                        .userRole(command.userRole())
                        .stateCondition(command.stateCondition())
                        .dataVariant(command.dataVariant())
                        .requestSpec(command.requestSpec())
                        .expectedSpec(command.expectedSpec())
                        .assertionSpec(command.assertionSpec())
                        .duplicate(command.duplicate())
                        .selectedForSave(false)
                        .createdAt(LocalDateTime.now())
                        .build());
                if (command.duplicate()) {
                    duplicateCount++;
                } else {
                    newCount++;
                }
            }
            generation.markCompleted(
                    commands.size() - duplicateCount,
                    newCount,
                    duplicateCount,
                    generation.getCurrentCoverage() == null
                            ? null
                            : generation.getCurrentCoverage().add(BigDecimal.valueOf(newCount * 5.0)).min(BigDecimal.valueOf(100.0))
            );
        } catch (Exception exception) {
            generation.markFailed();
        }
    }

    @Transactional(readOnly = true)
    public TestGeneration getGeneration(Long generationId) {
        return testGenerationRepository.findById(generationId)
                .orElseThrow(() -> new flowops.global.exception.ApiException(flowops.global.response.ErrorCode.RESOURCE_NOT_FOUND, "테스트 생성 요청을 찾을 수 없습니다."));
    }

    private TestCaseType parseType(String type) {
        try {
            return TestCaseType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            throw new flowops.global.exception.ApiException(
                    flowops.global.response.ErrorCode.INVALID_INPUT,
                    "지원하지 않는 테스트 케이스 유형입니다: " + type
            );
        }
    }

    private TestLevel resolveTestLevel(String requestedTestLevel, TestGeneration generation) {
        if (requestedTestLevel == null || requestedTestLevel.isBlank()) {
            return generation.getEnvironment() == null ? TestLevel.REGRESSION : generation.getEnvironment().getDefaultTestLevel();
        }
        try {
            return TestLevel.valueOf(requestedTestLevel.trim());
        } catch (IllegalArgumentException exception) {
            throw new flowops.global.exception.ApiException(
                    flowops.global.response.ErrorCode.INVALID_INPUT,
                    "지원하지 않는 테스트 레벨입니다: " + requestedTestLevel
            );
        }
    }

    private String defaultIfBlank(String requestedValue, String draftValue) {
        return requestedValue == null || requestedValue.isBlank() ? draftValue : requestedValue.trim();
    }

    private boolean isEdited(GeneratedTestCaseDraft draft, TestCaseDraftSaveRequest request) {
        return differs(request.name(), draft.getTitle())
                || differs(request.description(), draft.getDescription())
                || differs(request.type(), draft.getType())
                || differs(request.userRole(), draft.getUserRole())
                || differs(request.stateCondition(), draft.getStateCondition())
                || differs(request.dataVariant(), draft.getDataVariant())
                || differs(request.requestSpec(), draft.getRequestSpec())
                || differs(request.expectedResult(), draft.getExpectedSpec())
                || differs(request.assertionSpec(), draft.getAssertionSpec());
    }

    private boolean differs(String requestedValue, String draftValue) {
        if (requestedValue == null || requestedValue.isBlank()) {
            return false;
        }
        return !Objects.equals(requestedValue.trim(), draftValue);
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private BigDecimal toPredictedCoverage(Double currentCoverage) {
        if (currentCoverage == null) {
            return null;
        }
        return BigDecimal.valueOf(Math.min(100.0, currentCoverage + 10.0));
    }

    private ApiEndpoint resolveApiEndpoint(ExecutionStepLog failedLog) {
        if (failedLog.getTestCase() != null) {
            return failedLog.getTestCase().getApiEndpoint();
        }
        if (failedLog.getScenarioStep() != null) {
            return failedLog.getScenarioStep().getApiEndpoint();
        }
        throw new IllegalStateException("실패 로그가 API 엔드포인트와 연결되어 있지 않습니다.");
    }

    private ApiEndpoint endpointForInventory(ApiInventory apiInventory) {
        ApiMethod method = ApiMethod.valueOf(apiInventory.getMethod().name());
        return apiEndpointService.findFirstByMethodAndPath(method, apiInventory.getEndpointPath());
    }

    private void validateInventoryEnvironment(ApiInventory apiInventory, Environment environment) {
        if (apiInventory == null || environment == null) {
            return;
        }
        boolean repositoryMatches = environment.getRepositoryInfo() == null
                || (apiInventory.getRepositoryInfo() != null
                && apiInventory.getRepositoryInfo().getId().equals(environment.getRepositoryInfo().getId()));
        boolean branchMatches = environment.getBranchName() == null
                || environment.getBranchName().isBlank()
                || environment.getBranchName().equals(apiInventory.getBranchName());
        if (!repositoryMatches || !branchMatches) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "선택한 API 인벤토리가 테스트 생성 환경에 속하지 않습니다.");
        }
    }

    private GeneratedTestCaseDraft createFailureDraft(TestGeneration generation, ExecutionStepLog failedLog, boolean duplicate) {
        ApiEndpoint apiEndpoint = resolveApiEndpoint(failedLog);
        return draftRepository.save(GeneratedTestCaseDraft.builder()
                .generation(generation)
                .apiEndpoint(apiEndpoint)
                .title(apiEndpoint.getMethod() + " " + apiEndpoint.getPath() + " failure reproduction")
                .description("실패 재현과 원인 분석을 위한 기본 테스트 초안입니다.")
                .type(TestCaseType.FAILURE_HANDLING.name())
                .userRole("QA_ENGINEER")
                .stateCondition("실패를 재현할 수 있는 동일한 환경과 데이터가 준비되어 있어야 합니다.")
                .dataVariant("failure-reproduction")
                .requestSpec(failedLog.getRequestBody())
                .expectedSpec(expectedBehavior(failedLog))
                .assertionSpec("""
                        {
                          "goal": "Reproduce and isolate failure",
                          "errorMessage": "%s",
                          "assertions": [
                            "status != %s",
                            "response contains failure signal"
                          ]
                        }
                        """.formatted(
                        safeText(failedLog.getErrorMessage()),
                        failedLog.getResponseCode() == null ? "200" : failedLog.getResponseCode()
                ).trim())
                .duplicate(duplicate)
                .selectedForSave(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private GeneratedTestCaseDraft createFailureRegressionDraft(TestGeneration generation, ExecutionStepLog failedLog) {
        ApiEndpoint apiEndpoint = resolveApiEndpoint(failedLog);
        return draftRepository.save(GeneratedTestCaseDraft.builder()
                .generation(generation)
                .apiEndpoint(apiEndpoint)
                .title(apiEndpoint.getMethod() + " " + apiEndpoint.getPath() + " recovery regression")
                .description("같은 장애가 다시 발생하지 않도록 방지하는 회귀 테스트 초안입니다.")
                .type(TestCaseType.VALIDATION.name())
                .userRole("QA_ENGINEER")
                .stateCondition("에러가 수정된 이후 동일 요청이 정상 처리되어야 합니다.")
                .dataVariant("post-fix-regression")
                .requestSpec(failedLog.getRequestBody())
                .expectedSpec("""
                        {
                          "expectedBehavior": %s,
                          "status": 200
                        }
                        """.formatted(toJsonString(expectedBehavior(failedLog))).trim())
                .assertionSpec("""
                        {
                          "expectedBehavior": %s,
                          "actualBehaviorAtFailure": %s,
                          "assertions": [
                            "status == 200",
                            "response should not contain previous failure message"
                          ]
                        }
                        """.formatted(
                        toJsonString(expectedBehavior(failedLog)),
                        toJsonString(actualBehavior(failedLog))
                ).trim())
                .duplicate(false)
                .selectedForSave(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String buildFailureContextSummary(Execution execution, ExecutionStepLog failedLog) {
        ApiEndpoint apiEndpoint = resolveApiEndpoint(failedLog);
        return "Execution "
                + execution.getId()
                + " failed on "
                + apiEndpoint.getMethod()
                + " "
                + apiEndpoint.getPath()
                + ". Error: "
                + safeText(failedLog.getErrorMessage())
                + ". Expected: "
                + safeText(expectedBehavior(failedLog))
                + ". Actual: "
                + safeText(actualBehavior(failedLog));
    }

    private String expectedBehavior(ExecutionStepLog failedLog) {
        if (failedLog.getTestCase() != null && failedLog.getTestCase().getExpectedSpec() != null) {
            return failedLog.getTestCase().getExpectedSpec();
        }
        if (failedLog.getScenarioStep() != null && failedLog.getScenarioStep().getValidationRules() != null) {
            return failedLog.getScenarioStep().getValidationRules();
        }
        return "{\"status\":200}";
    }

    private String actualBehavior(ExecutionStepLog failedLog) {
        return """
                {
                  "statusCode": %s,
                  "responseBody": %s
                }
                """.formatted(
                failedLog.getResponseCode() == null ? "null" : failedLog.getResponseCode(),
                toJsonString(failedLog.getResponseBody())
        ).trim();
    }

    private String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
