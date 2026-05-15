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

@RestController
@RequiredArgsConstructor
@Tag(name = "Execution", description = "Execution history and logs API")
public class ExecutionController {

    private final ExecutionQueryService executionQueryService;

    @GetMapping("/executions")
    @Operation(summary = "Execution list", description = "Returns execution summaries.")
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
    @Operation(summary = "Execution detail", description = "Returns an execution with step logs.")
    public ApiResponse<ExecutionDetailResponse> getExecution(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getExecution(executionId));
    }

    @GetMapping("/executions/{executionId}/logs")
    @Operation(summary = "Execution step logs", description = "Returns step logs for a single execution.")
    public ApiResponse<List<ExecutionStepLogResponse>> getLogs(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getLogs(executionId));
    }

    @GetMapping({"/api/v1/executions/logs", "/executions/logs"})
    @Operation(summary = "Execution Logs list", description = "Returns step-based rows for the Execution Logs screen.")
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
    @Operation(summary = "Execution Log detail", description = "Returns request, response, and validation data for a step log.")
    public ApiResponse<ExecutionLogDetailResponse> getExecutionLogDetail(@PathVariable Long stepId) {
        return ApiResponse.success(executionQueryService.getExecutionLogDetail(stepId));
    }

    @GetMapping("/execution-step-logs/{logId}")
    @Operation(summary = "Legacy execution step log detail", description = "Returns raw request and response bodies for a step log.")
    public ApiResponse<ExecutionStepLogDetailResponse> getLogDetail(@PathVariable Long logId) {
        return ApiResponse.success(executionQueryService.getLogDetail(logId));
    }
}
