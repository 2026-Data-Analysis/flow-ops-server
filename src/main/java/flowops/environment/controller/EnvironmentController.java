package flowops.environment.controller;

import flowops.environment.dto.request.CreateEnvironmentRequest;
import flowops.environment.dto.request.UpdateEnvironmentRequest;
import flowops.environment.dto.request.UpdateTriggerRulesRequest;
import flowops.environment.dto.response.ConnectionTestResponse;
import flowops.environment.dto.response.EnvironmentResponse;
import flowops.environment.dto.response.TriggerRuleResponse;
import flowops.environment.service.EnvironmentService;
import flowops.environment.service.TriggerRuleService;
import flowops.global.response.ApiResponse;
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
 * 실행 환경과 환경별 트리거 규칙을 관리하는 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Environment", description = "실행 환경 및 트리거 규칙 관리 API")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final TriggerRuleService triggerRuleService;

    @PostMapping("/apps/{appId}/environments")
    @Operation(summary = "환경 생성", description = "앱에 대한 실행 환경 설정을 생성합니다.")
    public ApiResponse<EnvironmentResponse> createEnvironment(
            @PathVariable Long appId,
            @Valid @RequestBody CreateEnvironmentRequest request
    ) {
        return ApiResponse.success(environmentService.createEnvironment(appId, request));
    }

    @GetMapping("/apps/{appId}/environments")
    @Operation(summary = "환경 목록 조회", description = "앱에 연결된 환경 목록을 조회합니다.")
    public ApiResponse<List<EnvironmentResponse>> getEnvironments(@PathVariable Long appId) {
        return ApiResponse.success(environmentService.listByApp(appId));
    }

    @PatchMapping("/environments/{environmentId}")
    @Operation(summary = "환경 수정", description = "환경의 URL, 인증, 기본 테스트 레벨을 수정합니다.")
    public ApiResponse<EnvironmentResponse> updateEnvironment(
            @PathVariable Long environmentId,
            @Valid @RequestBody UpdateEnvironmentRequest request
    ) {
        return ApiResponse.success(environmentService.updateEnvironment(environmentId, request));
    }

    @PostMapping("/environments/{environmentId}/test-connection")
    @Operation(summary = "환경 연결 테스트", description = "실제 외부 호출 없이 연결 테스트용 placeholder 응답을 반환합니다.")
    public ApiResponse<ConnectionTestResponse> testConnection(@PathVariable Long environmentId) {
        return ApiResponse.success(environmentService.testConnection(environmentId));
    }

    @GetMapping("/environments/{environmentId}/triggers")
    @Operation(summary = "트리거 규칙 목록 조회", description = "환경에 연결된 자동 실행 규칙 목록을 조회합니다.")
    public ApiResponse<List<TriggerRuleResponse>> getTriggers(@PathVariable Long environmentId) {
        return ApiResponse.success(triggerRuleService.listByEnvironment(environmentId));
    }

    @PatchMapping("/environments/{environmentId}/triggers")
    @Operation(summary = "트리거 규칙 생성/수정", description = "환경의 자동화 트리거 규칙을 일괄 생성 또는 수정합니다.")
    public ApiResponse<List<TriggerRuleResponse>> updateTriggers(
            @PathVariable Long environmentId,
            @Valid @RequestBody UpdateTriggerRulesRequest request
    ) {
        return ApiResponse.success(triggerRuleService.updateRules(environmentId, request));
    }
}
