package flowops.execution.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.service.ApiEndpointService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.ExecutionMode;
import flowops.environment.domain.entity.Environment;
import flowops.environment.service.EnvironmentService;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.dto.request.CreateExecutionRequest;
import flowops.execution.dto.request.GenerateFailureTestCasesRequest;
import flowops.execution.dto.request.RunApisExecutionRequest;
import flowops.execution.dto.request.RunQuickTestRequest;
import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.GenerateFailureTestCasesResponse;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.service.ScenarioService;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testcase.service.TestCaseService;
import flowops.testgeneration.service.TestGenerationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트 실행 요청과 재실행, 취소 같은 실행 command를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class RunTestService {

    private final ExecutionRepository executionRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final AppService appService;
    private final EnvironmentService environmentService;
    private final ApiEndpointService apiEndpointService;
    private final TestCaseService testCaseService;
    private final TestCaseRepository testCaseRepository;
    private final ScenarioService scenarioService;
    private final TestGenerationService testGenerationService;

    @Transactional
    public ExecutionDetailResponse createExecution(CreateExecutionRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = environmentService.getEnvironment(request.environmentId());
        TestLevel executionTestLevel = resolveTestLevel(environment, request.testLevel());
        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(request.executionType())
                .targetId(request.targetId())
                .triggerSource(request.triggerSource())
                .executionMode(request.executionMode())
                .testLevel(executionTestLevel)
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = generateMockLogs(execution);
        completeExecution(execution, logs);
        return toDetail(execution, logs);
    }

    @Transactional
    public ExecutionDetailResponse runApis(RunApisExecutionRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = environmentService.getEnvironment(request.environmentId());
        TestLevel executionTestLevel = resolveTestLevel(environment, request.testLevel());
        Long targetId = request.apiIds().get(0);
        List<ApiEndpoint> apiEndpoints = request.apiIds().stream()
                .map(apiEndpointService::getApiEndpoint)
                .toList();
        if (request.executionMode() == ExecutionMode.GENERATE_AND_RUN) {
            ensureGeneratedTestCases(app, apiEndpoints, executionTestLevel);
        }

        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(request.apiIds().size() == 1 ? ExecutionType.API : ExecutionType.API_BATCH)
                .targetId(targetId)
                .triggerSource(flowops.execution.domain.entity.ExecutionTriggerSource.MANUAL)
                .executionMode(request.executionMode())
                .testLevel(executionTestLevel)
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = generateApiSelectionLogs(execution, request.apiIds(), executionTestLevel);
        completeExecution(execution, logs);
        return toDetail(execution, logs);
    }

    @Transactional
    public ExecutionDetailResponse runQuickTest(Long environmentId, RunQuickTestRequest request) {
        Environment environment = environmentService.getEnvironment(environmentId);
        App app = environment.getApp();
        TestLevel executionTestLevel = request.testLevel() == null ? environment.getDefaultTestLevel() : request.testLevel();
        List<TestCase> selectedTestCases = pickOneTestCasePerDomain(app.getId(), executionTestLevel);
        if (selectedTestCases.isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Quick Test로 실행할 수 있는 활성 테스트 케이스가 없습니다.");
        }

        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(selectedTestCases.size() == 1 ? ExecutionType.TEST_CASE : ExecutionType.API_BATCH)
                .targetId(selectedTestCases.get(0).getId())
                .triggerSource(flowops.execution.domain.entity.ExecutionTriggerSource.MANUAL)
                .executionMode(request.executionMode() == null ? ExecutionMode.RUN_EXISTING : request.executionMode())
                .testLevel(executionTestLevel)
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = selectedTestCases.stream()
                .map(testCase -> saveLog(
                        execution,
                        testCase,
                        null,
                        testCase.getApiEndpoint().getMethod().name(),
                        testCase.getApiEndpoint().getPath(),
                        testCase.getName(),
                        false
                ))
                .toList();
        completeExecution(execution, logs);
        return toDetail(execution, logs);
    }

    @Transactional
    public GenerateFailureTestCasesResponse generateFailureTestCases(Long executionId, GenerateFailureTestCasesRequest request) {
        Execution execution = findExecution(executionId);
        List<ExecutionStepLog> failedLogs = executionStepLogRepository.findByExecutionIdAndStatusOrderByCreatedAtAsc(executionId, ExecutionStepStatus.FAILED);
        if (failedLogs.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_STATE, "실패한 실행 로그가 없어 실패 기반 테스트 케이스를 생성할 수 없습니다.");
        }
        ExecutionStepLog failedLog = resolveFailedLog(failedLogs, request.failedLogId());
        return testGenerationService.generateFromFailure(execution, failedLog, request.requestedBy(), request.currentCoverage());
    }

    @Transactional
    public ExecutionDetailResponse rerun(Long executionId) {
        Execution previous = findExecution(executionId);
        if (previous.getEnvironment() == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "환경 정보가 없는 실행은 다시 실행할 수 없습니다.");
        }
        return createExecution(new CreateExecutionRequest(
                previous.getApp().getId(),
                previous.getEnvironment().getId(),
                previous.getExecutionType(),
                previous.getTargetId(),
                previous.getTriggerSource(),
                previous.getExecutionMode(),
                previous.getTestLevel(),
                previous.getCreatedBy()
        ));
    }

    @Transactional
    public ExecutionDetailResponse rerunFailed(Long executionId) {
        Execution previous = findExecution(executionId);
        List<ExecutionStepLog> failedLogs = executionStepLogRepository.findByExecutionIdAndStatusOrderByCreatedAtAsc(executionId, ExecutionStepStatus.FAILED);
        Execution rerun = executionRepository.save(Execution.builder()
                .app(previous.getApp())
                .environment(previous.getEnvironment())
                .executionType(previous.getExecutionType())
                .targetId(previous.getTargetId())
                .triggerSource(previous.getTriggerSource())
                .executionMode(previous.getExecutionMode())
                .testLevel(previous.getTestLevel())
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .createdBy(previous.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .build());
        rerun.markRunning();

        List<ExecutionStepLog> rerunLogs = new ArrayList<>();
        for (ExecutionStepLog failed : failedLogs) {
            rerunLogs.add(executionStepLogRepository.save(ExecutionStepLog.builder()
                    .execution(rerun)
                    .testCase(failed.getTestCase())
                    .scenarioStep(failed.getScenarioStep())
                    .stepName(failed.getStepName() + " (rerun)")
                    .method(failed.getMethod())
                    .path(failed.getPath())
                    .status(ExecutionStepStatus.SUCCESS)
                    .requestBody(failed.getRequestBody())
                    .responseBody("{\"result\":\"rerun success\"}")
                    .responseCode(200)
                    .durationMs(120L)
                    .startedAt(LocalDateTime.now())
                    .endedAt(LocalDateTime.now().plusNanos(120_000_000))
                    .createdAt(LocalDateTime.now())
                    .build()));
        }

        rerun.complete(rerunLogs.size(), rerunLogs.size(), 0, rerunLogs.isEmpty() ? 0L : 120L);
        return toDetail(rerun, rerunLogs);
    }

    @Transactional
    public ExecutionDetailResponse cancel(Long executionId) {
        Execution execution = findExecution(executionId);
        execution.cancel();
        List<ExecutionStepLog> logs = executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId);
        return toDetail(execution, logs);
    }

    private Execution findExecution(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "실행 정보를 찾을 수 없습니다."));
    }

    private ExecutionDetailResponse toDetail(Execution execution, List<ExecutionStepLog> logs) {
        return ExecutionDetailResponse.of(execution, logs, logs.stream().map(ExecutionStepLogResponse::from).toList());
    }

    private void completeExecution(Execution execution, List<ExecutionStepLog> logs) {
        long totalDuration = logs.stream().mapToLong(log -> log.getDurationMs() == null ? 0L : log.getDurationMs()).sum();
        execution.complete(
                logs.size(),
                (int) logs.stream().filter(log -> log.getStatus() == ExecutionStepStatus.SUCCESS).count(),
                (int) logs.stream().filter(log -> log.getStatus() == ExecutionStepStatus.FAILED).count(),
                logs.isEmpty() ? 0L : totalDuration / logs.size()
        );
    }

    private List<ExecutionStepLog> generateMockLogs(Execution execution) {
        if (execution.getExecutionType() == ExecutionType.API) {
            ApiEndpoint api = apiEndpointService.getApiEndpoint(execution.getTargetId());
            return List.of(saveLog(execution, null, null, api.getMethod().name(), api.getPath(), "API execution", false));
        }
        if (execution.getExecutionType() == ExecutionType.TEST_CASE) {
            TestCase testCase = testCaseService.getActiveTestCase(execution.getTargetId());
            return List.of(saveLog(execution, testCase, null, testCase.getApiEndpoint().getMethod().name(), testCase.getApiEndpoint().getPath(), testCase.getName(), false));
        }
        Scenario scenario = scenarioService.getScenario(execution.getTargetId());
        return scenarioService.getDetail(scenario.getId()).steps().stream()
                .map(step -> saveLog(
                        execution,
                        null,
                        null,
                        "POST",
                        "/scenarios/" + scenario.getId() + "/steps/" + step.id(),
                        step.label(),
                        step.stepOrder() % 3 == 0
                ))
                .toList();
    }

    private List<ExecutionStepLog> generateApiSelectionLogs(Execution execution, List<Long> apiIds, TestLevel testLevel) {
        List<TestCase> testCases = testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(apiIds)
                .stream()
                .filter(testCase -> testCase.getTestLevel() == testLevel)
                .toList();
        if (!testCases.isEmpty()) {
            return testCases.stream()
                    .map(testCase -> saveLog(
                            execution,
                            testCase,
                            null,
                            testCase.getApiEndpoint().getMethod().name(),
                            testCase.getApiEndpoint().getPath(),
                            testCase.getName(),
                            false
                    ))
                    .toList();
        }

        return apiIds.stream()
                .map(apiEndpointService::getApiEndpoint)
                .map(api -> saveLog(execution, null, null, api.getMethod().name(), api.getPath(), "API execution", false))
                .toList();
    }

    private List<TestCase> pickOneTestCasePerDomain(Long appId, TestLevel testLevel) {
        Map<String, List<TestCase>> groupedByDomain = testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(appId)
                .stream()
                .filter(testCase -> testCase.getTestLevel() == testLevel)
                .collect(Collectors.groupingBy(testCase -> {
                    String domainTag = testCase.getApiEndpoint().getDomainTag();
                    return domainTag == null || domainTag.isBlank() ? "UNTAGGED" : domainTag;
                }));

        return groupedByDomain.values().stream()
                .map(candidates -> candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())))
                .toList();
    }

    private TestLevel resolveTestLevel(Environment environment, TestLevel requestedTestLevel) {
        return requestedTestLevel == null ? environment.getDefaultTestLevel() : requestedTestLevel;
    }

    private ExecutionStepLog resolveFailedLog(List<ExecutionStepLog> failedLogs, Long failedLogId) {
        if (failedLogId == null) {
            return failedLogs.get(0);
        }
        return failedLogs.stream()
                .filter(log -> log.getId().equals(failedLogId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "지정한 실패 로그를 찾을 수 없습니다."));
    }

    private void ensureGeneratedTestCases(App app, List<ApiEndpoint> apiEndpoints, TestLevel testLevel) {
        for (ApiEndpoint apiEndpoint : apiEndpoints) {
            boolean exists = testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(apiEndpoint.getId())
                    .stream()
                    .anyMatch(testCase -> testCase.getTestLevel() == testLevel);
            if (exists) {
                continue;
            }
            testCaseRepository.save(TestCase.builder()
                    .app(app)
                    .apiEndpoint(apiEndpoint)
                    .name(apiEndpoint.getMethod() + " " + apiEndpoint.getPath() + " 기본 자동 테스트")
                    .description("일괄 생성 실행 요청으로 생성된 기본 테스트 케이스입니다.")
                    .type(TestCaseType.HAPPY_PATH)
                    .testLevel(testLevel)
                    .source(TestCaseSource.AUTO)
                    .requestSpec("{}")
                    .expectedSpec("{\"status\":200}")
                    .assertionSpec("{\"assertions\":[\"status == 200\"]}")
                    .active(true)
                    .version(1)
                    .build());
        }
    }

    private ExecutionStepLog saveLog(
            Execution execution,
            TestCase testCase,
            ScenarioStep scenarioStep,
            String method,
            String path,
            String stepName,
            boolean fail
    ) {
        LocalDateTime startedAt = LocalDateTime.now();
        return executionStepLogRepository.save(ExecutionStepLog.builder()
                .execution(execution)
                .testCase(testCase)
                .scenarioStep(scenarioStep)
                .stepName(stepName)
                .method(method)
                .path(path)
                .status(fail ? ExecutionStepStatus.FAILED : ExecutionStepStatus.SUCCESS)
                .requestBody("{\"request\":\"mock\"}")
                .responseBody(fail ? "{\"error\":\"mock failure\"}" : "{\"result\":\"mock success\"}")
                .responseCode(fail ? 500 : 200)
                .durationMs(fail ? 250L : 150L)
                .errorMessage(fail ? "Mock execution failure for placeholder engine." : null)
                .startedAt(startedAt)
                .endedAt(startedAt.plusNanos((fail ? 250L : 150L) * 1_000_000))
                .createdAt(startedAt)
                .build());
    }
}
