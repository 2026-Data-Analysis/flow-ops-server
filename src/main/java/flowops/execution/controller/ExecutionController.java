package flowops.execution.controller;

import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.ExecutionSummaryResponse;
import flowops.execution.service.ExecutionQueryService;
import flowops.global.response.ApiResponse;
import flowops.global.response.PageResponse;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실행 이력과 로그 조회 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Execution", description = "실행 이력 및 실행 로그 조회 API")
public class ExecutionController {

    private final ExecutionQueryService executionQueryService;

    @GetMapping("/executions")
    @Operation(summary = "실행 목록 조회", description = "환경, 테스트 레벨, 실패 여부, Slow 기준으로 실행 목록을 조회합니다.")
    public ApiResponse<PageResponse<ExecutionSummaryResponse>> listExecutions(
            @Parameter(description = "환경 ID 필터")
            @RequestParam(required = false) Long environmentId,
            @Parameter(description = "테스트 레벨 필터")
            @RequestParam(required = false) TestLevel testLevel,
            @Parameter(description = "생성자 키워드 필터")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "실패 건만 조회 여부")
            @RequestParam(defaultValue = "false") boolean failedOnly,
            @Parameter(description = "평균 실행 시간 200ms 초과 Slow 건만 조회 여부")
            @RequestParam(defaultValue = "false") boolean slowOnly,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(executionQueryService.listExecutions(environmentId, testLevel, keyword, failedOnly, slowOnly, pageable));
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "실행 상세 조회", description = "실행 요약과 단계별 로그를 함께 조회합니다.")
    public ApiResponse<ExecutionDetailResponse> getExecution(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getExecution(executionId));
    }

    @GetMapping("/executions/{executionId}/logs")
    @Operation(summary = "실행 로그 목록 조회", description = "테이블 뷰용 실행 스텝 로그 목록을 조회합니다.")
    public ApiResponse<List<ExecutionStepLogResponse>> getLogs(@PathVariable Long executionId) {
        return ApiResponse.success(executionQueryService.getLogs(executionId));
    }

    @GetMapping("/execution-step-logs/{logId}")
    @Operation(summary = "실행 로그 상세 조회", description = "요청/응답 정보를 포함한 실행 로그 상세를 조회합니다.")
    public ApiResponse<ExecutionStepLogDetailResponse> getLogDetail(@PathVariable Long logId) {
        return ApiResponse.success(executionQueryService.getLogDetail(logId));
    }
}
