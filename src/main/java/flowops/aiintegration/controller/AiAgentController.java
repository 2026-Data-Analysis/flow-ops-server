package flowops.aiintegration.controller;

import flowops.aiintegration.client.AiClient;
import flowops.global.response.ApiResponse;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
import flowops.integration.ai.AiAgentContracts.LogAnalysisRequest;
import flowops.integration.ai.AiAgentContracts.LogAnalysisResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierRequest;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/agents")
@RequiredArgsConstructor
@Tag(name = "AI 에이전트", description = "테스트 케이스, 시나리오, 로그 분석, 장애 리포트 AI 연동 API")
public class AiAgentController {

    private final AiClient aiClient;

    @PostMapping("/test-cases/generate")
    @Operation(summary = "테스트 케이스 초안 생성", description = "선택한 API와 기존 테스트 정보를 바탕으로 AI 테스트 케이스 초안을 생성합니다.")
    public ApiResponse<TestCaseGeneratorResponse> generateTestCases(
            @Valid @RequestBody TestCaseGeneratorRequest request
    ) {
        return ApiResponse.success(aiClient.generateTestCaseDrafts(request));
    }

    @PostMapping("/scenarios/build")
    @Operation(summary = "API 시나리오 생성", description = "사용자 의도와 API 목록을 바탕으로 실행 가능한 시나리오 스텝을 추천합니다.")
    public ApiResponse<ScenarioGenerateResponse> buildScenario(
            @Valid @RequestBody ScenarioGenerateRequest request
    ) {
        return ApiResponse.success(aiClient.buildScenario(request));
    }

    @PostMapping("/logs/analyze")
    @Operation(summary = "실패 로그 분석", description = "실행 로그, 요청/응답, 검증 결과를 바탕으로 실패 원인과 조치 방안을 분석합니다.")
    public ApiResponse<LogAnalysisResponse> analyzeLog(
            @Valid @RequestBody LogAnalysisRequest request
    ) {
        return ApiResponse.success(aiClient.analyzeLog(request));
    }

    @PostMapping("/error-reports/generate")
    @Operation(summary = "장애 리포트 생성", description = "실행 실패 정보와 로그 분석 결과를 바탕으로 대상 독자에 맞는 장애 리포트를 생성합니다.")
    public ApiResponse<ErrorReportResponse> generateErrorReport(
            @Valid @RequestBody ErrorReportRequest request
    ) {
        return ApiResponse.success(aiClient.generateErrorReport(request));
    }

    @PostMapping("/test-strategy/classify")
    @Operation(summary = "테스트 위험도 분류", description = "생성된 테스트 후보를 SMOKE, SANITY, REGRESSION, FULL 테스트 레벨로 분류합니다.")
    public ApiResponse<TestStrategyClassifierResponse> classifyTestStrategy(
            @Valid @RequestBody TestStrategyClassifierRequest request
    ) {
        return ApiResponse.success(aiClient.classifyTestStrategy(request));
    }
}
