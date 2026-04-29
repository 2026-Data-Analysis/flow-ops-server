package flowops.app.controller;

import flowops.app.dto.request.CreateAppRequest;
import flowops.app.dto.response.AppDetailResponse;
import flowops.app.service.AppService;
import flowops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 대상 애플리케이션 등록과 상세 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/apps")
@RequiredArgsConstructor
@Tag(name = "App", description = "애플리케이션 등록 및 상세 조회 API")
public class AppController {

    private final AppService appService;

    @PostMapping
    @Operation(summary = "앱 생성", description = "FlowOps에서 관리할 애플리케이션을 생성합니다.")
    public ApiResponse<AppDetailResponse> createApp(@Valid @RequestBody CreateAppRequest request) {
        return ApiResponse.success(appService.createApp(request));
    }

    @GetMapping("/{appId}")
    @Operation(summary = "앱 상세 조회", description = "앱 기본 정보와 메타데이터를 조회합니다.")
    public ApiResponse<AppDetailResponse> getApp(@PathVariable Long appId) {
        return ApiResponse.success(appService.getAppDetail(appId));
    }
}
