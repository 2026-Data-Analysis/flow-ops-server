package flowops.execution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.dto.response.ExecutionDetailResponse;
import flowops.execution.dto.response.ExecutionLogDetailResponse;
import flowops.execution.dto.response.ExecutionLogListItemResponse;
import flowops.execution.dto.response.ExecutionLogListResponse;
import flowops.execution.dto.response.ExecutionStepLogDetailResponse;
import flowops.execution.dto.response.ExecutionStepLogResponse;
import flowops.execution.dto.response.ExecutionSummaryResponse;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.repository.TestValidationResultRepository;
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

@Service
@RequiredArgsConstructor
public class ExecutionQueryService {

    private final ExecutionRepository executionRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final TestValidationResultRepository testValidationResultRepository;
    private final ObjectMapper objectMapper;

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
                        normalize(keyword),
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
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Execution step log not found."));
        return ExecutionStepLogDetailResponse.from(log);
    }

    @Transactional(readOnly = true)
    public ExecutionLogListResponse getExecutionLogs(
            String keyword,
            boolean failedOnly,
            boolean slowOnly,
            String environment,
            TestLevel testLevel
    ) {
        List<ExecutionLogListItemResponse> items = executionStepLogRepository.findLogRows(
                        normalize(keyword),
                        failedOnly,
                        slowOnly,
                        normalize(environment),
                        testLevel
                )
                .stream()
                .map(ExecutionLogListItemResponse::from)
                .toList();
        return new ExecutionLogListResponse(items);
    }

    @Transactional(readOnly = true)
    public ExecutionLogDetailResponse getExecutionLogDetail(Long stepId) {
        ExecutionStepLog log = executionStepLogRepository.findById(stepId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Execution log step not found."));
        List<ExecutionStepLog> executionTimeline = executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(log.getExecution().getId());
        return ExecutionLogDetailResponse.of(
                log,
                parseJson(log.getExecution().getEnvironment() == null ? null : log.getExecution().getEnvironment().getHeaders()),
                parseJson(log.getRequestBody()),
                parseJson(log.getResponseBody()),
                testValidationResultRepository.findByExecutionStepIdOrderByIdAsc(stepId),
                executionTimeline
        );
    }

    @Transactional(readOnly = true)
    public Execution findExecution(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Execution not found."));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }
}
