package flowops.api.controller;

import flowops.api.domain.entity.ApiMethod;
import flowops.api.dto.response.ApiEndpointDetailResponse;
import flowops.api.dto.response.ApiEndpointListItemResponse;
import flowops.api.service.ApiEndpointService;
import flowops.global.response.ApiResponse;
import flowops.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 애플리케이션에 수집된 API 엔드포인트 조회 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "API Endpoint", description = "앱별 API 엔드포인트 조회 API")
public class ApiEndpointController {

    private final ApiEndpointService apiEndpointService;

    @GetMapping("/apps/{appId}/apis")
    @Operation(summary = "앱의 API 목록 조회", description = "도메인 태그와 HTTP 메서드 기준으로 API 엔드포인트를 필터링해서 조회합니다.")
    public ApiResponse<PageResponse<ApiEndpointListItemResponse>> getApisByApp(
            @PathVariable Long appId,
            @Parameter(description = "도메인 태그 필터")
            @RequestParam(required = false) String domainTag,
            @Parameter(description = "HTTP 메서드 필터")
            @RequestParam(required = false) ApiMethod method,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.success(apiEndpointService.getApiEndpoints(appId, domainTag, method, pageable));
    }

    @GetMapping("/apis/{apiId}")
    @Operation(summary = "API 상세 조회", description = "요청/응답 스키마와 테스트 집계 정보를 포함한 API 상세를 조회합니다.")
    public ApiResponse<ApiEndpointDetailResponse> getApiDetail(@PathVariable Long apiId) {
        return ApiResponse.success(apiEndpointService.getApiEndpointDetail(apiId));
    }
}
