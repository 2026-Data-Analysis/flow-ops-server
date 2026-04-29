package flowops.execution.service;

import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.ExecutionSummaryResponse;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.global.response.PageResponse;
import flowops.testcase.domain.entity.TestLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실행 이력 조회와 로그 조회를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionQueryService {

    private final ExecutionRepository executionRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;

    @Transactional(readOnly = true)
    public PageResponse<ExecutionSummaryResponse> listExecutions(
            Long environmentId,
            TestLevel testLevel,
            String keyword,
            boolean failedOnly,
            boolean slowOnly,
            Pageable pageable
    ) {
        Page<ExecutionSummaryResponse> page = executionRepository.findByFilters(
                        environmentId,
                        testLevel,
                        keyword,
                        failedOnly,
                        slowOnly,
                        pageable
                )
                .map(execution -> ExecutionSummaryResponse.from(
                        execution,
                        executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(execution.getId())
                ));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ExecutionDetailResponse getExecution(Long executionId) {
        Execution execution = findExecution(executionId);
        List<ExecutionStepLog> logs = executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId);
        return ExecutionDetailResponse.of(execution, logs, logs.stream().map(ExecutionStepLogResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public List<ExecutionStepLogResponse> getLogs(Long executionId) {
        findExecution(executionId);
        return executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(executionId)
                .stream()
                .map(ExecutionStepLogResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExecutionStepLogDetailResponse getLogDetail(Long logId) {
        ExecutionStepLog log = executionStepLogRepository.findById(logId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "실행 단계 로그를 찾을 수 없습니다."));
        return ExecutionStepLogDetailResponse.from(log);
    }

    @Transactional(readOnly = true)
    public Execution findExecution(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "실행 정보를 찾을 수 없습니다."));
    }
}
