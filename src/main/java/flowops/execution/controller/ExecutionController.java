package flowops.execution.controller;

import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionLogDetailResponse;
import flowops.execution.dto.response.ExecutionLogListResponse;
import flowops.execution.dto.response.ExecutionStepLogDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.ExecutionSummaryResponse;
import flowops.execution.service.ExecutionQueryService;
import flowops.global.response.ApiResponse;
import flowops.global.response.PageResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CommonApiErrorResponses
@RestController
@RequiredArgsConstructor
@Tag(name = "실행 이력", description = "실행 이력과 로그 조회 API")
public class ExecutionController {

    private final ExecutionQueryService executionQueryService;

    @GetMapping("/apps/{appId}/executions")
    @Operation(summary = "앱별 실행 이력 조회", description = "앱 ID로 실행 이력 목록을 조회합니다.")
    public ApiResponse<PageResponse<ExecutionDetailResponse>> listExecutionsByApp(
            @PathVariable Long appId,
            @PageableDefault(size = 100) Pageable pageable
    ) {
        return ApiResponse.success(executionQueryService.listExecutionsByApp(appId, pageable));
    }

    @GetMapping("/executions")
    @Operation(summary = "실행 목록 조회", description = "실행 이력 요약 목록을 조회합니다.")
    public ApiResponse<PageResponse<ExecutionSummaryResponse>> listExecutions(
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) TestLevel testLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @RequestParam(defaultValue = "false") boolean slowOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(executionQueryService.listExecutions(environmentId, testLevel, keyword, failedOnly, slowOnly, pageable));
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "실행 상세 조회", description = "실행 정보와 스텝 로그를 함께 조회합니다.")
    public ApiResponse<ExecutionDetailResponse> getExecution(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getExecution(executionId));
    }

    @GetMapping("/executions/{executionId}/logs")
    @Operation(summary = "실행 스텝 로그 조회", description = "단일 실행에 속한 스텝 로그 목록을 조회합니다.")
    public ApiResponse<List<ExecutionStepLogResponse>> getLogs(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getLogs(executionId));
    }

    @GetMapping({"/api/v1/executions/logs", "/executions/logs"})
    @Operation(summary = "실행 로그 목록 조회", description = "Execution Logs 화면에서 사용할 스텝 단위 로그 목록을 조회합니다.")
    public ApiResponse<ExecutionLogListResponse> getExecutionLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @RequestParam(defaultValue = "false") boolean slowOnly,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) TestLevel testLevel
    ) {
        return ApiResponse.success(executionQueryService.getExecutionLogs(keyword, failedOnly, slowOnly, environment, testLevel));
    }

    @GetMapping({"/api/v1/executions/logs/{stepId}", "/executions/logs/{stepId}"})
    @Operation(summary = "실행 로그 상세 조회", description = "스텝 로그의 요청, 응답, 검증 결과를 상세 조회합니다.")
    public ApiResponse<ExecutionLogDetailResponse> getExecutionLogDetail(@PathVariable Long stepId) {
        return ApiResponse.success(executionQueryService.getExecutionLogDetail(stepId));
    }

    @GetMapping("/execution-step-logs/{logId}")
    @Operation(summary = "레거시 실행 스텝 로그 상세 조회", description = "스텝 로그의 원본 요청/응답 바디를 조회합니다.")
    public ApiResponse<ExecutionStepLogDetailResponse> getLogDetail(@PathVariable Long logId) {
        return ApiResponse.success(executionQueryService.getLogDetail(logId));
    }
}
