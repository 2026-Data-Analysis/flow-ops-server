package flowops.testgeneration.controller;

import flowops.global.response.ApiResponse;
import flowops.testgeneration.dto.request.CreateTestGenerationRequest;
import flowops.testgeneration.dto.request.SaveGeneratedDraftsRequest;
import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import flowops.testgeneration.dto.response.SaveGeneratedDraftsResponse;
import flowops.testgeneration.dto.response.TestGenerationDetailResponse;
import flowops.testgeneration.service.TestGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 테스트 생성 요청과 생성 초안 저장 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Test Generation", description = "AI 테스트 생성 요청 및 초안 관리 API")
public class TestGenerationController {

    private final TestGenerationService testGenerationService;

    @PostMapping("/test-generations")
    @Operation(summary = "테스트 생성 요청", description = "선택한 API들을 기준으로 테스트 생성 작업을 요청합니다.")
    public ApiResponse<TestGenerationDetailResponse> requestGeneration(
            @Valid @RequestBody CreateTestGenerationRequest request
    ) {
        return ApiResponse.success(testGenerationService.requestGeneration(request));
    }

    @GetMapping("/test-generations/{generationId}")
    @Operation(summary = "생성 작업 상세 조회", description = "테스트 생성 작업의 상태와 집계 정보를 조회합니다.")
    public ApiResponse<TestGenerationDetailResponse> getGeneration(@PathVariable Long generationId) {
        return ApiResponse.success(testGenerationService.getDetail(generationId));
    }

    @GetMapping("/test-generations/{generationId}/drafts")
    @Operation(summary = "생성 초안 목록 조회", description = "생성 작업에서 만들어진 테스트 초안 목록을 조회합니다.")
    public ApiResponse<List<GeneratedTestCaseDraftResponse>> getDrafts(@PathVariable Long generationId) {
        return ApiResponse.success(testGenerationService.getDrafts(generationId));
    }

    @PostMapping("/test-generations/{generationId}/save")
    @Operation(summary = "선택 초안 저장", description = "선택된 생성 초안을 실제 테스트 케이스로 저장합니다.")
    public ApiResponse<SaveGeneratedDraftsResponse> saveDrafts(
            @PathVariable Long generationId,
            @Valid @RequestBody SaveGeneratedDraftsRequest request
    ) {
        return ApiResponse.success(testGenerationService.saveDrafts(generationId, request));
    }
}
