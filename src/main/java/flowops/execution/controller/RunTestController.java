package flowops.execution.controller;

import flowops.execution.dto.request.CreateExecutionRequest;
import flowops.execution.dto.request.GenerateFailureTestCasesRequest;
import flowops.execution.dto.request.RunApisExecutionRequest;
import flowops.execution.dto.request.RunQuickTestRequest;
import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.GenerateFailureTestCasesResponse;
import flowops.execution.service.RunTestService;
import flowops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 실행 요청과 재실행 같은 실행 command API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Run Test", description = "테스트 실행 요청 API")
public class RunTestController {

    private final RunTestService runTestService;

    @PostMapping("/executions")
    @Operation(summary = "실행 생성", description = "API, 테스트 케이스, 시나리오 실행을 요청하고 mock 실행 결과를 저장합니다.")
    public ApiResponse<ExecutionDetailResponse> createExecution(@Valid @RequestBody CreateExecutionRequest request) {
        return ApiResponse.success(runTestService.createExecution(request));
    }

    @PostMapping("/executions/run-apis")
    @Operation(summary = "일괄 테스트 실행/생성", description = "API ID 목록 기준으로 기존 테스트를 실행하거나 GENERATE_AND_RUN 모드에서 테스트를 생성 후 실행합니다.")
    public ApiResponse<ExecutionDetailResponse> runApis(@Valid @RequestBody RunApisExecutionRequest request) {
        return ApiResponse.success(runTestService.runApis(request));
    }

    @PostMapping("/executions/batch-tests")
    @Operation(summary = "일괄 테스트 실행/생성", description = "run-apis와 동일한 일괄 실행/생성 API입니다.")
    public ApiResponse<ExecutionDetailResponse> runBatchTests(@Valid @RequestBody RunApisExecutionRequest request) {
        return ApiResponse.success(runTestService.runApis(request));
    }

    @PostMapping("/environments/{environmentId}/quick-test")
    @Operation(summary = "환경 Quick Test 실행", description = "환경의 앱에서 도메인별 활성 테스트 케이스를 하나씩 선택해 즉시 실행합니다.")
    public ApiResponse<ExecutionDetailResponse> runQuickTest(
            @PathVariable Long environmentId,
            @Valid @RequestBody RunQuickTestRequest request
    ) {
        return ApiResponse.success(runTestService.runQuickTest(environmentId, request));
    }

    @PostMapping("/executions/{executionId}/generate-failure-test-cases")
    @Operation(summary = "실패 기반 테스트 케이스 생성", description = "실패 로그를 바탕으로 AI 연계용 테스트 케이스 초안을 생성합니다.")
    public ApiResponse<GenerateFailureTestCasesResponse> generateFailureTestCases(
            @PathVariable Long executionId,
            @Valid @RequestBody GenerateFailureTestCasesRequest request
    ) {
        return ApiResponse.success(runTestService.generateFailureTestCases(executionId, request));
    }

    @PostMapping("/executions/{executionId}/rerun")
    @Operation(summary = "실행 재시도", description = "기존 실행 설정을 기준으로 전체 재시도를 수행합니다.")
    public ApiResponse<ExecutionDetailResponse> rerun(@PathVariable Long executionId) {
        return ApiResponse.success(runTestService.rerun(executionId));
    }

    @PostMapping("/executions/{executionId}/rerun-failed")
    @Operation(summary = "실패 건만 재시도", description = "이전 실행에서 실패한 로그만 골라 재시도합니다.")
    public ApiResponse<ExecutionDetailResponse> rerunFailed(@PathVariable Long executionId) {
        return ApiResponse.success(runTestService.rerunFailed(executionId));
    }

    @PostMapping("/executions/{executionId}/cancel")
    @Operation(summary = "실행 취소", description = "실행 상태를 취소로 변경합니다.")
    public ApiResponse<ExecutionDetailResponse> cancel(@PathVariable Long executionId) {
        return ApiResponse.success(runTestService.cancel(executionId));
    }
}
