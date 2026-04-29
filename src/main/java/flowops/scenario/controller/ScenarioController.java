package flowops.scenario.controller;

import flowops.global.response.ApiResponse;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.ReorderScenarioStepsRequest;
import flowops.scenario.dto.request.UpdateScenarioRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioRecommendationResponse;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import flowops.scenario.service.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시나리오 추천, 생성, 수정, 단계 재정렬 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Scenario", description = "시나리오 추천 및 빌더 관리 API")
public class ScenarioController {

    private final ScenarioService scenarioService;

    @PostMapping("/scenarios/recommend")
    @Operation(summary = "시나리오 추천", description = "시나리오 빌더에서 사용할 추천 시나리오 목록을 반환합니다.")
    public ApiResponse<List<ScenarioRecommendationResponse>> recommendScenarios() {
        return ApiResponse.success(scenarioService.recommend());
    }

    @PostMapping("/scenarios")
    @Operation(summary = "시나리오 생성", description = "앱 기준 시나리오와 스텝 목록을 생성합니다.")
    public ApiResponse<ScenarioDetailResponse> createScenario(@Valid @RequestBody CreateScenarioRequest request) {
        return ApiResponse.success(scenarioService.create(request));
    }

    @GetMapping("/apps/{appId}/scenarios")
    @Operation(summary = "시나리오 목록 조회", description = "앱에 속한 시나리오 목록을 조회합니다.")
    public ApiResponse<List<ScenarioSummaryResponse>> getScenariosByApp(@PathVariable Long appId) {
        return ApiResponse.success(scenarioService.listByApp(appId));
    }

    @GetMapping("/scenarios/{scenarioId}")
    @Operation(summary = "시나리오 상세 조회", description = "시나리오 메타데이터와 정렬된 스텝 목록을 조회합니다.")
    public ApiResponse<ScenarioDetailResponse> getScenario(@PathVariable Long scenarioId) {
        return ApiResponse.success(scenarioService.getDetail(scenarioId));
    }

    @PatchMapping("/scenarios/{scenarioId}")
    @Operation(summary = "시나리오 수정", description = "시나리오 메타데이터와 선택적으로 스텝 목록을 수정합니다.")
    public ApiResponse<ScenarioDetailResponse> updateScenario(
            @PathVariable Long scenarioId,
            @Valid @RequestBody UpdateScenarioRequest request
    ) {
        return ApiResponse.success(scenarioService.update(scenarioId, request));
    }

    @PatchMapping("/scenarios/{scenarioId}/steps/reorder")
    @Operation(summary = "시나리오 스텝 순서 변경", description = "시나리오 스텝의 순서를 일괄 수정합니다.")
    public ApiResponse<ScenarioDetailResponse> reorderScenarioSteps(
            @PathVariable Long scenarioId,
            @Valid @RequestBody ReorderScenarioStepsRequest request
    ) {
        return ApiResponse.success(scenarioService.reorderSteps(scenarioId, request));
    }
}
