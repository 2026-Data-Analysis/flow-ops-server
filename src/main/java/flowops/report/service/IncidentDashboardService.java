package flowops.report.service;

import flowops.app.service.AppService;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.repository.ExecutionRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.support.ExecutionViewSupport;
import flowops.report.dto.response.IncidentDashboardResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실행/에러 로그를 기반으로 Incident Dashboard 집계 데이터를 생성합니다.
 */
@Service
@RequiredArgsConstructor
public class IncidentDashboardService {

    private static final Set<ExecutionStatus> INCIDENT_STATUSES = EnumSet.of(
            ExecutionStatus.FAILED,
            ExecutionStatus.PARTIAL_FAILED
    );

    private final AppService appService;
    private final ExecutionRepository executionRepository;
    private final ExecutionStepLogRepository executionStepLogRepository;

    @Transactional(readOnly = true)
    public IncidentDashboardResponse getDashboard(
            Long appId,
            Long environmentId,
            String riskLevel,
            LocalDate from,
            LocalDate to,
            int days
    ) {
        appService.getApp(appId);
        DashboardPeriod period = resolvePeriod(from, to, days);
        RiskLevel risk = RiskLevel.from(riskLevel);
        long periodDays = Math.max(1, Duration.between(period.from(), period.to()).toDays());
        LocalDateTime previousFrom = period.from().minusDays(periodDays);

        List<Execution> currentExecutions = executionRepository.findDashboardExecutions(appId, environmentId, period.from(), period.to());
        List<Execution> previousExecutions = executionRepository.findDashboardExecutions(appId, environmentId, previousFrom, period.from());
        List<ExecutionStepLog> currentLogs = findLogs(currentExecutions);
        List<ExecutionStepLog> previousLogs = findLogs(previousExecutions);
        List<ExecutionStepLog> currentRiskLogs = filterByRisk(currentLogs, risk);
        List<ExecutionStepLog> previousRiskLogs = filterByRisk(previousLogs, risk);

        DashboardMetrics currentMetrics = calculateMetrics(currentExecutions, currentRiskLogs, period.days(), period.from().toLocalDate());
        DashboardMetrics previousMetrics = calculateMetrics(previousExecutions, previousRiskLogs, period.days(), previousFrom.toLocalDate());

        return new IncidentDashboardResponse(
                currentMetrics.successRate,
                currentMetrics.totalTests,
                currentMetrics.failedTests,
                currentMetrics.avgDurationMs,
                metric(currentMetrics.totalErrors, previousMetrics.totalErrors),
                metric(currentMetrics.criticalErrors, previousMetrics.criticalErrors),
                metric(currentMetrics.recurring, previousMetrics.recurring),
                metric(currentMetrics.failureRate, previousMetrics.failureRate),
                metric(currentMetrics.mttrMinutes, previousMetrics.mttrMinutes),
                metric(currentMetrics.affectedApis, previousMetrics.affectedApis),
                currentMetrics.testResultsTrend,
                currentMetrics.errorDistribution,
                currentMetrics.errorsByEnvironment,
                currentMetrics.topFailingApis,
                currentMetrics.recentIncidents
        );
    }

    private List<ExecutionStepLog> findLogs(List<Execution> executions) {
        List<Long> executionIds = executions.stream().map(Execution::getId).toList();
        if (executionIds.isEmpty()) {
            return List.of();
        }
        return executionStepLogRepository.findByExecutionIdIn(executionIds);
    }

    private DashboardPeriod resolvePeriod(LocalDate from, LocalDate to, int days) {
        int safeDays = Math.max(days, 1);
        LocalDate currentToDate = to == null ? LocalDate.now().plusDays(1) : to.plusDays(1);
        LocalDate currentFromDate = from == null ? currentToDate.minusDays(safeDays) : from;
        if (!currentFromDate.isBefore(currentToDate)) {
            currentFromDate = currentToDate.minusDays(1);
        }
        return new DashboardPeriod(
                currentFromDate.atStartOfDay(),
                currentToDate.atStartOfDay(),
                (int) Math.max(1, Duration.between(currentFromDate.atStartOfDay(), currentToDate.atStartOfDay()).toDays())
        );
    }

    private List<ExecutionStepLog> filterByRisk(List<ExecutionStepLog> logs, RiskLevel riskLevel) {
        if (riskLevel == RiskLevel.ALL) {
            return logs;
        }
        return logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .filter(log -> classifyRisk(log) == riskLevel)
                .toList();
    }

    private DashboardMetrics calculateMetrics(
            List<Execution> executions,
            List<ExecutionStepLog> logs,
            int days,
            LocalDate startDate
    ) {
        int totalTests = executions.stream()
                .map(Execution::getTotalCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int failedTests = executions.stream()
                .map(Execution::getFailedCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int passedTests = executions.stream()
                .map(Execution::getPassedCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        double successRate = totalTests == 0 ? 0.0 : round((passedTests * 100.0) / totalTests);
        double failureRate = totalTests == 0 ? 0.0 : round((failedTests * 100.0) / totalTests);
        double avgDurationMs = round(executions.stream()
                .map(Execution::getAvgDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0));
        long totalErrors = logs.stream().filter(log -> log.getStatus() == ExecutionStepStatus.FAILED).count();
        long criticalErrors = logs.stream().filter(this::isCriticalError).count();
        long recurring = recurringCount(logs);
        long affectedApis = logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .map(this::endpointLabel)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .count();
        double mttrMinutes = round(meanTimeToRecoveryMinutes(executions));

        return new DashboardMetrics(
                successRate,
                totalTests,
                failedTests,
                avgDurationMs,
                totalErrors,
                criticalErrors,
                recurring,
                failureRate,
                mttrMinutes,
                affectedApis,
                buildTrend(executions, days, startDate),
                buildErrorDistribution(logs),
                buildErrorsByEnvironment(logs),
                buildTopFailingApis(logs),
                buildRecentIncidents(executions, logs)
        );
    }

    private List<IncidentDashboardResponse.TestResultTrendPointResponse> buildTrend(
            List<Execution> executions,
            int days,
            LocalDate startDate
    ) {
        Map<LocalDate, Integer> passedByDate = new HashMap<>();
        Map<LocalDate, Integer> failedByDate = new HashMap<>();
        for (Execution execution : executions) {
            LocalDate date = executedAt(execution).toLocalDate();
            passedByDate.merge(date, execution.getPassedCount() == null ? 0 : execution.getPassedCount(), Integer::sum);
            failedByDate.merge(date, execution.getFailedCount() == null ? 0 : execution.getFailedCount(), Integer::sum);
        }

        List<IncidentDashboardResponse.TestResultTrendPointResponse> points = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            points.add(new IncidentDashboardResponse.TestResultTrendPointResponse(
                    date,
                    passedByDate.getOrDefault(date, 0),
                    failedByDate.getOrDefault(date, 0),
                    passedByDate.getOrDefault(date, 0),
                    failedByDate.getOrDefault(date, 0)
            ));
        }
        return points;
    }

    private List<IncidentDashboardResponse.ErrorDistributionResponse> buildErrorDistribution(List<ExecutionStepLog> logs) {
        return logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .collect(Collectors.groupingBy(this::errorLabel, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new IncidentDashboardResponse.ErrorDistributionResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<IncidentDashboardResponse.EnvironmentErrorResponse> buildErrorsByEnvironment(List<ExecutionStepLog> logs) {
        Map<String, List<ExecutionStepLog>> grouped = logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .collect(Collectors.groupingBy(log -> {
                    if (log.getExecution().getEnvironment() == null) {
                        return "null::Unassigned";
                    }
                    return log.getExecution().getEnvironment().getId() + "::" + log.getExecution().getEnvironment().getName();
                }));

        return grouped.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("::", 2);
                    Long environmentId = "null".equals(parts[0]) ? null : Long.valueOf(parts[0]);
                    return new IncidentDashboardResponse.EnvironmentErrorResponse(
                            environmentId,
                            parts.length > 1 ? parts[1] : "Unassigned",
                            entry.getValue().size()
                    );
                })
                .sorted(Comparator.comparingLong(IncidentDashboardResponse.EnvironmentErrorResponse::errorCount).reversed())
                .toList();
    }

    private List<IncidentDashboardResponse.TopFailingApiResponse> buildTopFailingApis(List<ExecutionStepLog> logs) {
        return logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .collect(Collectors.groupingBy(log -> environmentEndpointKey(log), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(4)
                .map(entry -> toTopFailingApi(entry.getKey(), entry.getValue()))
                .toList();
    }

    private IncidentDashboardResponse.TopFailingApiResponse toTopFailingApi(String key, long count) {
        String[] parts = key.split("\\|\\|", 3);
        Long environmentId = "null".equals(parts[0]) ? null : Long.valueOf(parts[0]);
        String environmentName = parts[1];
        return new IncidentDashboardResponse.TopFailingApiResponse(
                environmentId,
                environmentName,
                parts[2],
                count
        );
    }

    private List<IncidentDashboardResponse.RecentIncidentResponse> buildRecentIncidents(
            List<Execution> executions,
            List<ExecutionStepLog> logs
    ) {
        Map<Long, List<ExecutionStepLog>> logsByExecution = logs.stream()
                .collect(Collectors.groupingBy(log -> log.getExecution().getId()));

        return executions.stream()
                .filter(execution -> INCIDENT_STATUSES.contains(execution.getStatus()))
                .sorted(Comparator.comparing(Execution::getCreatedAt).reversed())
                .limit(4)
                .map(execution -> {
                    List<ExecutionStepLog> executionLogs = logsByExecution.getOrDefault(execution.getId(), List.of());
                    ExecutionStepLog firstLog = executionLogs.stream()
                            .sorted(Comparator.comparing(ExecutionStepLog::getCreatedAt))
                            .findFirst()
                            .orElse(null);
                    ExecutionStepLog failedLog = executionLogs.stream()
                            .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                            .findFirst()
                            .orElse(firstLog);
                    return new IncidentDashboardResponse.RecentIncidentResponse(
                            execution.getId(),
                            ExecutionViewSupport.executedAt(execution, firstLog),
                            ExecutionViewSupport.caseName(execution, firstLog),
                            execution.getEnvironment() == null ? "Unassigned" : execution.getEnvironment().getName(),
                            execution.getStatus(),
                            failedLog == null ? null : failedLog.getPath(),
                            failedLog == null ? null : failedLog.getErrorMessage()
                    );
                })
                .toList();
    }

    private long recurringCount(List<ExecutionStepLog> logs) {
        return logs.stream()
                .filter(log -> log.getStatus() == ExecutionStepStatus.FAILED)
                .collect(Collectors.groupingBy(this::recurringSignature, Collectors.counting()))
                .values()
                .stream()
                .filter(count -> count > 1)
                .mapToLong(Long::longValue)
                .sum();
    }

    private double meanTimeToRecoveryMinutes(List<Execution> executions) {
        List<Execution> ordered = executions.stream()
                .sorted(Comparator.comparing(Execution::getCreatedAt))
                .toList();
        List<Long> recoveries = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Execution failed = ordered.get(i);
            if (!INCIDENT_STATUSES.contains(failed.getStatus())) {
                continue;
            }
            for (int j = i + 1; j < ordered.size(); j++) {
                Execution candidate = ordered.get(j);
                if (!sameRecoveryScope(failed, candidate)) {
                    continue;
                }
                if (candidate.getStatus() == ExecutionStatus.SUCCESS) {
                    LocalDateTime from = failed.getEndedAt() == null ? failed.getCreatedAt() : failed.getEndedAt();
                    LocalDateTime to = candidate.getStartedAt() == null ? candidate.getCreatedAt() : candidate.getStartedAt();
                    recoveries.add(Duration.between(from, to).toMinutes());
                    break;
                }
            }
        }
        return recoveries.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private boolean sameRecoveryScope(Execution failed, Execution candidate) {
        if (!Objects.equals(failed.getApp().getId(), candidate.getApp().getId())) {
            return false;
        }
        Long failedEnvironmentId = failed.getEnvironment() == null ? null : failed.getEnvironment().getId();
        Long candidateEnvironmentId = candidate.getEnvironment() == null ? null : candidate.getEnvironment().getId();
        return Objects.equals(failedEnvironmentId, candidateEnvironmentId);
    }

    private boolean isCriticalError(ExecutionStepLog log) {
        return classifyRisk(log) == RiskLevel.CRITICAL;
    }

    private RiskLevel classifyRisk(ExecutionStepLog log) {
        Integer code = log.getResponseCode();
        String message = normalizeText(log.getErrorMessage());
        if ((code != null && code >= 500)
                || message.contains("timeout")
                || message.contains("connection refused")
                || message.contains("exception")) {
            return RiskLevel.CRITICAL;
        }
        if (code != null && code >= 400) {
            return RiskLevel.HIGH;
        }
        if (!message.isBlank()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String errorLabel(ExecutionStepLog log) {
        String message = normalizeText(log.getErrorMessage());
        Integer code = log.getResponseCode();
        if (message.contains("timeout")) {
            return "Timeout";
        }
        if (code != null && code == 404) {
            return "404 Not Found";
        }
        if (code != null && code >= 500) {
            return code + " Error";
        }
        if (message.contains("connection refused")) {
            return "Connection Refused";
        }
        if (message.isBlank()) {
            return code == null ? "Unknown Error" : code + " Error";
        }
        return shorten(log.getErrorMessage(), 80);
    }

    private String recurringSignature(ExecutionStepLog log) {
        String endpoint = endpointLabel(log);
        return endpoint + "::" + errorLabel(log);
    }

    private String environmentEndpointKey(ExecutionStepLog log) {
        Long environmentId = log.getExecution().getEnvironment() == null ? null : log.getExecution().getEnvironment().getId();
        String environmentName = log.getExecution().getEnvironment() == null ? "Unassigned" : log.getExecution().getEnvironment().getName();
        return (environmentId == null ? "null" : environmentId) + "||" + environmentName + "||" + endpointLabel(log);
    }

    private String endpointLabel(ExecutionStepLog log) {
        String method = log.getMethod() == null ? "" : log.getMethod() + " ";
        return method + (log.getPath() == null ? "" : log.getPath());
    }

    private LocalDateTime executedAt(Execution execution) {
        if (execution.getStartedAt() != null) {
            return execution.getStartedAt();
        }
        if (execution.getEndedAt() != null) {
            return execution.getEndedAt();
        }
        return execution.getCreatedAt();
    }

    private IncidentDashboardResponse.MetricChangeResponse metric(double current, double previous) {
        return new IncidentDashboardResponse.MetricChangeResponse(
                round(current),
                round(percentChange(current, previous)),
                round(current - previous)
        );
    }

    private double percentChange(double current, double previous) {
        if (previous == 0.0) {
            return current == 0.0 ? 0.0 : 100.0;
        }
        return ((current - previous) / previous) * 100.0;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private record DashboardMetrics(
            double successRate,
            int totalTests,
            int failedTests,
            double avgDurationMs,
            long totalErrors,
            long criticalErrors,
            long recurring,
            double failureRate,
            double mttrMinutes,
            long affectedApis,
            List<IncidentDashboardResponse.TestResultTrendPointResponse> testResultsTrend,
            List<IncidentDashboardResponse.ErrorDistributionResponse> errorDistribution,
            List<IncidentDashboardResponse.EnvironmentErrorResponse> errorsByEnvironment,
            List<IncidentDashboardResponse.TopFailingApiResponse> topFailingApis,
            List<IncidentDashboardResponse.RecentIncidentResponse> recentIncidents
    ) {
    }

    private record DashboardPeriod(LocalDateTime from, LocalDateTime to, int days) {
    }

    private enum RiskLevel {
        ALL,
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW;

        static RiskLevel from(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            try {
                return RiskLevel.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }
}
