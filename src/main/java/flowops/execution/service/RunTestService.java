package flowops.execution.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
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
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.dto.request.CreateExecutionRequest;
import flowops.execution.dto.request.GenerateFailureTestCasesRequest;
import flowops.execution.dto.request.RunApisExecutionRequest;
import flowops.execution.dto.request.RunQuickTestRequest;
import flowops.execution.dto.request.RunScenarioRequest;
import flowops.execution.dto.request.RunTestCasesRequest;
import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.GenerateFailureTestCasesResponse;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.scenario.domain.entity.Scenario;
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
    private final EnvironmentRepository environmentRepository;
    private final ApiEndpointService apiEndpointService;
    private final TestCaseService testCaseService;
    private final TestCaseRepository testCaseRepository;
    private final ScenarioService scenarioService;
    private final TestGenerationService testGenerationService;
    private final HttpExecutionEngine httpExecutionEngine;

    @Transactional
    public ExecutionDetailResponse createExecution(CreateExecutionRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = resolveEnvironment(app.getId(), request.environmentId());
        TestLevel executionTestLevel = resolveTestLevel(environment, request.testLevel());
        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(request.executionType())
                .targetId(request.targetId())
                .triggerSource(request.triggerSource())
                .executionMode(request.executionMode())
                .testLevel(executionTestLevel)
                .name(resolveExecutionName(request.executionType(), request.targetId()))
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode(request.tearDownMode()))
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = executeTarget(execution);
        logs = refreshExecutionLogs(execution, logs);
        completeExecution(execution, logs);
        return toDetail(execution, logs);
    }

    @Transactional
    public ExecutionDetailResponse runApis(RunApisExecutionRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = resolveEnvironment(app.getId(), request.environmentId());
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
                .name(request.apiIds().size() == 1 ? "API execution" : "API batch execution")
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode(request.tearDownMode()))
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = httpExecutionEngine.executeApiSelection(execution, request.apiIds(), executionTestLevel);
        logs = refreshExecutionLogs(execution, logs);
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
                .name("Quick Test")
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode(request.tearDownMode()))
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = selectedTestCases.stream()
                .map(testCase -> httpExecutionEngine.executeTestCase(execution, testCase))
                .toList();
        logs = refreshExecutionLogs(execution, logs);
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
                previous.getCreatedBy(),
                previous.isTearDownMode()
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
                .name(previous.getName() + " (rerun failed)")
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(previous.isTearDownMode())
                .createdBy(previous.getCreatedBy())
                .createdAt(LocalDateTime.now())
                .build());
        rerun.markRunning();

        List<ExecutionStepLog> rerunLogs = new ArrayList<>();
        for (ExecutionStepLog failed : failedLogs) {
            if (failed.getTestCase() != null) {
                rerunLogs.add(httpExecutionEngine.executeTestCase(rerun, failed.getTestCase(), failed.getStepName() + " (rerun)"));
            } else if (failed.getScenarioStep() != null) {
                rerunLogs.add(httpExecutionEngine.executeScenarioStep(rerun, failed.getScenarioStep(), failed.getStepName() + " (rerun)"));
            } else if (failed.getMethod() != null && failed.getPath() != null) {
                ApiEndpoint api = apiEndpointService.findFirstByMethodAndPath(ApiMethod.valueOf(failed.getMethod()), failed.getPath());
                rerunLogs.add(httpExecutionEngine.executeApi(rerun, api, failed.getStepName() + " (rerun)"));
            }
        }

        rerunLogs = refreshExecutionLogs(rerun, rerunLogs);
        completeExecution(rerun, rerunLogs);
        return toDetail(rerun, rerunLogs);
    }

    @Transactional
    public ExecutionDetailResponse runScenario(RunScenarioRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = resolveEnvironment(request.appId(), request.environmentId());
        TestLevel executionTestLevel = resolveTestLevel(environment, request.testLevel());

        List<Long> ids = new ArrayList<>();
        if (request.scenarioIds() != null && !request.scenarioIds().isEmpty()) {
            ids.addAll(request.scenarioIds());
        } else if (request.scenarioId() != null) {
            ids.add(request.scenarioId());
        }
        if (ids.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "실행할 시나리오를 하나 이상 선택해야 합니다.");
        }

        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(ExecutionType.SCENARIO)
                .targetId(ids.get(0))
                .triggerSource(flowops.execution.domain.entity.ExecutionTriggerSource.MANUAL)
                .executionMode(ExecutionMode.RUN_EXISTING)
                .testLevel(executionTestLevel)
                .name(ids.size() == 1 ? "Scenario execution" : "Scenario batch execution")
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode(request.tearDownMode()))
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = new ArrayList<>();
        for (Long scenarioId : ids) {
            scenarioService.getScenario(scenarioId);
            if (httpExecutionEngine.countScenarioSteps(scenarioId) == 0) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario has no executable steps.");
            }
            logs.addAll(httpExecutionEngine.executeScenario(execution, scenarioId));
        }
        logs = refreshExecutionLogs(execution, logs);
        completeExecution(execution, logs);
        return toDetail(execution, logs);
    }

    @Transactional
    public ExecutionDetailResponse runTestCases(RunTestCasesRequest request) {
        App app = appService.getApp(request.appId());
        Environment environment = resolveEnvironment(request.appId(), request.environmentId());
        TestLevel executionTestLevel = resolveTestLevel(environment, request.testLevel());

        List<TestCase> testCases;
        if (request.testCaseIds() != null && !request.testCaseIds().isEmpty()) {
            testCases = request.testCaseIds().stream()
                    .map(testCaseService::getActiveTestCase)
                    .toList();
        } else {
            testCases = testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(app.getId());
        }
        if (testCases.isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "실행할 수 있는 활성 테스트 케이스가 없습니다.");
        }

        Execution execution = executionRepository.save(Execution.builder()
                .app(app)
                .environment(environment)
                .executionType(testCases.size() == 1 ? ExecutionType.TEST_CASE : ExecutionType.API_BATCH)
                .targetId(testCases.get(0).getId())
                .triggerSource(flowops.execution.domain.entity.ExecutionTriggerSource.MANUAL)
                .executionMode(ExecutionMode.RUN_EXISTING)
                .testLevel(executionTestLevel)
                .name(testCases.size() == 1 ? "Test case execution" : "Test case batch execution")
                .status(ExecutionStatus.QUEUED)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode(request.tearDownMode()))
                .createdBy(request.createdBy())
                .createdAt(LocalDateTime.now())
                .build());

        execution.markRunning();
        List<ExecutionStepLog> logs = testCases.stream()
                .map(testCase -> httpExecutionEngine.executeTestCase(execution, testCase))
                .toList();
        logs = refreshExecutionLogs(execution, logs);
        completeExecution(execution, logs);
        return toDetail(execution, logs);
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

    private List<ExecutionStepLog> refreshExecutionLogs(Execution execution, List<ExecutionStepLog> fallbackLogs) {
        if (execution.getId() == null) {
            return fallbackLogs;
        }
        return executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(execution.getId());
    }

    private void completeExecution(Execution execution, List<ExecutionStepLog> logs) {
        long totalDuration = logs.stream().mapToLong(log -> log.getDurationMs() == null ? 0L : log.getDurationMs()).sum();
        int failedCount = (int) logs.stream().filter(log -> log.getStatus() == ExecutionStepStatus.FAILED).count();
        int passedCount = (int) logs.stream().filter(log -> log.getStatus() == ExecutionStepStatus.SUCCESS).count();
        execution.complete(
                logs.size(),
                passedCount,
                failedCount,
                logs.isEmpty() ? 0L : totalDuration / logs.size(),
                totalDuration,
                "Passed " + passedCount + " of " + logs.size() + " steps."
        );
    }

    private List<ExecutionStepLog> executeTarget(Execution execution) {
        if (execution.getExecutionType() == ExecutionType.API) {
            ApiEndpoint api = apiEndpointService.getApiEndpoint(execution.getTargetId());
            return List.of(httpExecutionEngine.executeApi(execution, api, "API execution"));
        }
        if (execution.getExecutionType() == ExecutionType.TEST_CASE) {
            TestCase testCase = testCaseService.getActiveTestCase(execution.getTargetId());
            return List.of(httpExecutionEngine.executeTestCase(execution, testCase));
        }
        Scenario scenario = scenarioService.getScenario(execution.getTargetId());
        if (httpExecutionEngine.countScenarioSteps(scenario.getId()) == 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "Scenario has no executable steps.");
        }
        return httpExecutionEngine.executeScenario(execution, scenario.getId());
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

    private Environment resolveEnvironment(Long appId, Long environmentId) {
        if (environmentId != null) {
            Environment environment = environmentService.getEnvironment(environmentId);
            if (!environment.getApp().getId().equals(appId)) {
                throw new ApiException(ErrorCode.INVALID_INPUT, "The selected environment does not belong to the requested app.");
            }
            return environment;
        }
        return environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(appId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                        "앱에 등록된 환경이 없습니다. 환경을 먼저 설정해 주세요."));
    }

    private TestLevel resolveTestLevel(Environment environment, TestLevel requestedTestLevel) {
        return requestedTestLevel == null ? environment.getDefaultTestLevel() : requestedTestLevel;
    }

    private boolean tearDownMode(Boolean tearDownMode) {
        return Boolean.TRUE.equals(tearDownMode);
    }

    private String resolveExecutionName(ExecutionType executionType, Long targetId) {
        if (executionType == ExecutionType.API) {
            ApiEndpoint api = apiEndpointService.getApiEndpoint(targetId);
            return api.getMethod() + " " + api.getPath();
        }
        if (executionType == ExecutionType.TEST_CASE) {
            return testCaseService.getActiveTestCase(targetId).getName();
        }
        if (executionType == ExecutionType.SCENARIO) {
            return scenarioService.getScenario(targetId).getName();
        }
        return "API batch execution";
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
                    .requestSpec(apiEndpoint.getRequestSchema() == null ? "{}" : apiEndpoint.getRequestSchema())
                    .expectedSpec("{\"status\":200}")
                    .assertionSpec("{\"assertions\":[\"status == 200\"]}")
                    .active(true)
                    .version(1)
                    .build());
        }
    }

}
