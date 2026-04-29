package flowops.report.controller;

import flowops.global.response.ApiResponse;
import flowops.report.dto.request.CreateIncidentReportRequest;
import flowops.report.dto.response.ReportResponse;
import flowops.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인시던트와 대상 독자 기준으로 자동 리포트 초안을 생성합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Report", description = "인시던트 리포트 생성 API")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/projects/{projectId}/reports/incidents")
    @Operation(summary = "인시던트 리포트 생성", description = "Incident와 target audience를 입력 받아 AI 친화적인 리포트 초안을 생성합니다.")
    public ApiResponse<ReportResponse> createIncidentReport(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateIncidentReportRequest request
    ) {
        return ApiResponse.success(reportService.createIncidentReport(projectId, request));
    }
}
