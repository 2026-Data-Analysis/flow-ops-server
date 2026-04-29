package flowops.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.environment.domain.entity.Environment;
import flowops.environment.service.EnvironmentService;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.service.ExecutionQueryService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.project.domain.entity.Project;
import flowops.project.service.ProjectService;
import flowops.report.domain.entity.Report;
import flowops.report.domain.entity.ReportType;
import flowops.report.domain.entity.TargetAudience;
import flowops.report.dto.request.CreateIncidentReportRequest;
import flowops.report.dto.response.ReportResponse;
import flowops.report.repository.ReportRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인시던트 입력과 실행 문맥을 바탕으로 AI 친화적인 리포트 초안을 생성합니다.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ProjectService projectService;
    private final ExecutionQueryService executionQueryService;
    private final ExecutionStepLogRepository executionStepLogRepository;
    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReportResponse createIncidentReport(Long projectId, CreateIncidentReportRequest request) {
        Project project = projectService.getProject(projectId);
        Execution execution = request.executionId() == null ? null : executionQueryService.findExecution(request.executionId());
        Environment environment = resolveEnvironment(execution, request.environmentId());
        List<ExecutionStepLog> logs = execution == null
                ? List.of()
                : executionStepLogRepository.findByExecutionIdOrderByCreatedAtAsc(execution.getId());

        ReportResponse.IncidentReportPayload payload = buildPayload(request, execution, environment, logs);
        Report report = reportRepository.save(Report.builder()
                .project(project)
                .execution(execution)
                .type(ReportType.INCIDENT)
                .title(buildTitle(request.targetAudience(), request.incident()))
                .reportPayload(writePayload(payload))
                .build());

        return ReportResponse.from(report, payload);
    }

    private Environment resolveEnvironment(Execution execution, Long environmentId) {
        if (execution != null && execution.getEnvironment() != null) {
            return execution.getEnvironment();
        }
        if (environmentId != null) {
            return environmentService.getEnvironment(environmentId);
        }
        return null;
    }

    private ReportResponse.IncidentReportPayload buildPayload(
            CreateIncidentReportRequest request,
            Execution execution,
            Environment environment,
            List<ExecutionStepLog> logs
    ) {
        ExecutionStepLog failedLog = logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .findFirst()
                .orElse(null);
        String summary = buildSummary(request.targetAudience(), request.incident(), environment, execution, failedLog);
        String impact = buildImpact(request.targetAudience(), execution, failedLog);
        List<String> nextActions = buildNextActions(request.targetAudience(), execution, failedLog);
        List<String> recommendedChannels = buildChannels(request.targetAudience());
        return new ReportResponse.IncidentReportPayload(
                request.incident(),
                request.targetAudience(),
                summary,
                impact,
                nextActions,
                recommendedChannels,
                environment == null ? null : environment.getName(),
                request.additionalContext()
        );
    }

    private String buildSummary(
            TargetAudience audience,
            String incident,
            Environment environment,
            Execution execution,
            ExecutionStepLog failedLog
    ) {
        String environmentName = environment == null ? "unknown environment" : environment.getName();
        String endpoint = failedLog == null ? null : failedLog.getPath();
        if (audience == TargetAudience.CUSTOMER) {
            return incident + " has been identified in " + environmentName
                    + ". We are investigating the issue and working to restore stable service."
                    + (endpoint == null ? "" : " Affected endpoint: " + endpoint + ".");
        }
        if (audience == TargetAudience.CS_TEAM) {
            return incident + " was observed in " + environmentName
                    + ". Customer-facing support should acknowledge intermittent failures"
                    + (endpoint == null ? "." : " around " + endpoint + ".")
                    + " Engineering is reviewing the incident.";
        }
        return incident + " was detected in " + environmentName
                + ". "
                + technicalExecutionSummary(execution, failedLog);
    }

    private String buildImpact(TargetAudience audience, Execution execution, ExecutionStepLog failedLog) {
        if (execution == null) {
            return audience == TargetAudience.CUSTOMER
                    ? "Impact is still being assessed."
                    : "No linked execution was provided, so impact is based on the incident title and operator context only.";
        }
        String base = "Execution " + execution.getId()
                + " finished with status " + execution.getStatus()
                + ", passed " + safeInt(execution.getPassedCount())
                + ", failed " + safeInt(execution.getFailedCount())
                + ", avg duration " + safeLong(execution.getAvgDurationMs()) + "ms.";
        if (audience == TargetAudience.CUSTOMER) {
            return "Some requests may have been delayed or failed while we investigate. " + base;
        }
        if (failedLog != null && failedLog.getErrorMessage() != null) {
            return base + " Primary error: " + failedLog.getErrorMessage();
        }
        return base;
    }

    private List<String> buildNextActions(TargetAudience audience, Execution execution, ExecutionStepLog failedLog) {
        if (audience == TargetAudience.CUSTOMER) {
            return List.of(
                    "Continue monitoring service stability and user impact.",
                    "Share a concise follow-up once mitigation is confirmed.",
                    "Avoid exposing internal root-cause details until verified."
            );
        }
        if (audience == TargetAudience.CS_TEAM) {
            return List.of(
                    "Use the approved customer-facing incident wording.",
                    "Collect affected account, timestamp, and endpoint details from inbound tickets.",
                    "Escalate repeated reports that match this incident signature."
            );
        }
        return List.of(
                "Inspect the failing execution and correlated infrastructure signals.",
                "Verify whether the incident is isolated to one environment or branch.",
                failedLog == null
                        ? "Create targeted regression coverage for this incident."
                        : "Generate failure-based regression tests for " + failedLog.getPath() + "."
        );
    }

    private List<String> buildChannels(TargetAudience audience) {
        return switch (audience) {
            case INTERNAL_TEAM -> List.of("Engineering Slack", "Incident Dashboard", "Ops Handoff");
            case CUSTOMER -> List.of("Status Page", "Customer Email", "Release Notes");
            case CS_TEAM -> List.of("Support Slack", "CS Playbook", "Ticket Macro Update");
        };
    }

    private String technicalExecutionSummary(Execution execution, ExecutionStepLog failedLog) {
        if (execution == null) {
            return "No linked execution was provided, so this draft is based on manual incident input.";
        }
        String error = failedLog == null || failedLog.getErrorMessage() == null
                ? "No primary error message captured."
                : failedLog.getErrorMessage();
        return "Execution status is " + execution.getStatus()
                + " with "
                + safeInt(execution.getFailedCount()) + " failed steps. "
                + error;
    }

    private String buildTitle(TargetAudience audience, String incident) {
        return "[" + audience.name() + "] " + incident;
    }

    private String writePayload(ReportResponse.IncidentReportPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "리포트 payload 직렬화에 실패했습니다.");
        }
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
