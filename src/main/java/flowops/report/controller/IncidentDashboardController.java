package flowops.report.controller;

import flowops.global.response.ApiResponse;
import flowops.report.dto.response.IncidentDashboardResponse;
import flowops.report.service.IncidentDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앱 단위 Incident Dashboard 집계 데이터를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Incident Dashboard", description = "장애/실행 집계 대시보드 API")
public class IncidentDashboardController {

    private final IncidentDashboardService incidentDashboardService;

    @GetMapping("/apps/{appId}/incident-dashboard")
    @Operation(summary = "Incident Dashboard 조회", description = "앱의 최근 실행/에러 로그를 집계해 Incident Dashboard 데이터를 반환합니다.")
    public ApiResponse<IncidentDashboardResponse> getDashboard(
            @PathVariable Long appId,
            @Parameter(description = "환경 ID 필터")
            @RequestParam(required = false) Long environmentId,
            @Parameter(description = "조회 기간(일)", example = "7")
            @RequestParam(defaultValue = "7") int days
    ) {
        return ApiResponse.success(incidentDashboardService.getDashboard(appId, environmentId, days));
    }
}
