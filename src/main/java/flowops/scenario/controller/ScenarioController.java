package flowops.scenario.controller;

import flowops.global.response.ApiResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.RecommendScenarioRequest;
import flowops.scenario.dto.request.ReorderScenarioStepsRequest;
import flowops.scenario.dto.request.ScenarioDraftSaveRequest;
import flowops.scenario.dto.request.UpdateScenarioRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioDraftBulkSaveResponse;
import flowops.scenario.dto.response.ScenarioDraftSaveResponse;
import flowops.scenario.dto.response.ScenarioRecommendationResponse;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import flowops.scenario.service.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시나리오 추천, 생성, 수정, 단계 재정렬 API를 제공합니다.
 */
@CommonApiErrorResponses
@RestController
@RequiredArgsConstructor
@Tag(name = "시나리오", description = "시나리오 추천 및 빌더 관리 API")
public class ScenarioController {

    private final ScenarioService scenarioService;

    @PostMapping("/scenarios/recommend")
    @Operation(summary = "시나리오 추천", description = "시나리오 빌더에서 사용할 추천 시나리오 목록을 반환합니다.")
    public ApiResponse<List<ScenarioRecommendationResponse>> recommendScenarios(
            @RequestBody(required = false) RecommendScenarioRequest request
    ) {
        return ApiResponse.success(scenarioService.recommend(request));
    }

    @PostMapping("/scenarios/v2/generate")
    @Operation(summary = "V2 시나리오 생성", description = "AI가 생성한 전체 스텝 명세(userRole, stateCondition, requestSpec 등)를 포함한 시나리오를 반환합니다.")
    public ApiResponse<ScenarioGenerateResponse> generateScenariosV2(
            @RequestBody(required = false) RecommendScenarioRequest request
    ) {
        return ApiResponse.success(scenarioService.generateV2(request));
    }

    @PostMapping("/scenarios")
    @Operation(summary = "시나리오 생성", description = "앱 기준 시나리오와 스텝 목록을 생성합니다.")
    public ApiResponse<ScenarioDetailResponse> createScenario(@Valid @RequestBody CreateScenarioRequest request) {
        return ApiResponse.success(scenarioService.create(request));
    }

    @PostMapping("/apps/{appId}/scenarios")
    @Operation(summary = "Save generated scenario draft", description = "Persists an orchestrator-generated scenario draft as a DB scenario.")
    public ApiResponse<ScenarioDraftSaveResponse> saveGeneratedScenarioDraft(
            @PathVariable Long appId,
            @RequestBody ScenarioDraftSaveRequest request
    ) {
        return ApiResponse.success(scenarioService.saveDraft(appId, request));
    }

    @PostMapping("/apps/{appId}/scenarios/bulk")
    @Operation(summary = "Bulk save generated scenario drafts", description = "Persists multiple orchestrator-generated scenario drafts.")
    public ApiResponse<ScenarioDraftBulkSaveResponse> saveGeneratedScenarioDrafts(
            @PathVariable Long appId,
            @RequestBody List<ScenarioDraftSaveRequest> request
    ) {
        return ApiResponse.success(scenarioService.saveDrafts(appId, request));
    }

    @GetMapping("/apps/{appId}/scenarios")
    @Operation(summary = "시나리오 목록 조회", description = "앱에 속한 시나리오 목록을 조회합니다.")
    public ApiResponse<List<ScenarioSummaryResponse>> getScenariosByApp(
            @PathVariable Long appId,
            @RequestParam(required = false) Long environmentId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) String domainTag,
            @RequestParam(required = false) String method
    ) {
        return ApiResponse.success(scenarioService.listByApp(appId, environmentId, repositoryId, branchName, domainTag, method));
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

    @DeleteMapping("/scenarios/{scenarioId}")
    @Operation(summary = "시나리오 삭제", description = "시나리오와 연결된 스텝을 삭제합니다.")
    public ApiResponse<Void> deleteScenario(@PathVariable Long scenarioId) {
        scenarioService.delete(scenarioId);
        return ApiResponse.success(null);
    }
}
