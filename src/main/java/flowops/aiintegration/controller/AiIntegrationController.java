package flowops.aiintegration.controller;

import flowops.aiintegration.dto.request.AnalyzeSpecRequest;
import flowops.aiintegration.dto.response.AiSuggestionResponse;
import flowops.aiintegration.service.AiSuggestionService;
import flowops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 AI 분석 요청을 프로젝트 제안 이력으로 남기는 API를 제공합니다.
 */
@RestController
@RequestMapping("/projects/{projectId}/ai")
@RequiredArgsConstructor
@Tag(name = "Legacy AI Integration", description = "기존 AI 분석 API")
public class AiIntegrationController {

    private final AiSuggestionService aiSuggestionService;

    @PostMapping("/spec-analysis")
    @Operation(summary = "스펙 분석", description = "프로젝트 스펙을 분석하고 AI 제안 결과를 저장합니다.")
    public ApiResponse<AiSuggestionResponse> analyzeSpec(
            @PathVariable Long projectId,
            @Valid @RequestBody AnalyzeSpecRequest request
    ) {
        return ApiResponse.success(aiSuggestionService.analyzeSpecAndStore(projectId, request));
    }
}
