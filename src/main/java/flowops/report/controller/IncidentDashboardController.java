package flowops.report.controller;

import flowops.global.response.ApiResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import flowops.report.dto.response.IncidentDashboardResponse;
import flowops.report.service.IncidentDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 단위 Incident Dashboard 집계 데이터를 제공합니다.
 */
@CommonApiErrorResponses
@RestController
@RequiredArgsConstructor
@Tag(name = "인시던트 대시보드", description = "장애/실행 집계 대시보드 API")
public class IncidentDashboardController {

    private final IncidentDashboardService incidentDashboardService;

    @GetMapping("/apps/{appId}/incident-dashboard")
    @Operation(summary = "인시던트 대시보드 조회", description = "앱의 최근 실행과 오류 로그를 집계해 인시던트 대시보드 데이터를 반환합니다.")
    public ApiResponse<IncidentDashboardResponse> getDashboard(
            @PathVariable Long appId,
            @Parameter(description = "환경 ID 필터")
            @RequestParam(required = false) Long environmentId,
            @Parameter(description = "위험도 필터(CRITICAL, HIGH, MEDIUM, LOW)")
            @RequestParam(required = false) String riskLevel,
            @Parameter(description = "조회 시작일", example = "2026-04-01")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @Parameter(description = "조회 종료일", example = "2026-04-08")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @Parameter(description = "조회 기간(일)", example = "7")
            @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.success(incidentDashboardService.getDashboard(appId, environmentId, riskLevel, from, to, days));
    }
}
