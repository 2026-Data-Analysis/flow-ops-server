package flowops.aiintegration.service;

import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.domain.entity.AiSuggestion;
import flowops.aiintegration.domain.entity.AiSuggestionStatus;
import flowops.aiintegration.domain.entity.AiSuggestionType;
import flowops.aiintegration.dto.request.AnalyzeSpecRequest;
import flowops.aiintegration.dto.response.AiSuggestionResponse;
import flowops.aiintegration.dto.response.AnalyzeSpecResponse;
import flowops.aiintegration.repository.AiSuggestionRepository;
import flowops.global.exception.ApiException;
import flowops.project.domain.entity.Project;
import flowops.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 분석 요청과 응답을 프로젝트 단위 제안 이력으로 저장합니다.
 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private final AiClient aiClient;
    private final AiSuggestionRepository aiSuggestionRepository;
    private final ProjectService projectService;

    @Transactional
    public AiSuggestionResponse analyzeSpecAndStore(Long projectId, AnalyzeSpecRequest request) {
        Project project = projectService.getProject(projectId);

        try {
            AnalyzeSpecResponse aiResponse = aiClient.analyzeSpec(request);
            AiSuggestion suggestion = aiSuggestionRepository.save(AiSuggestion.builder()
                    .project(project)
                    .suggestionType(AiSuggestionType.SPEC_ANALYSIS)
                    .status(AiSuggestionStatus.COMPLETED)
                    .inputText(request.specContent())
                    .outputText(aiResponse.summary() + System.lineSeparator() + aiResponse.recommendation())
                    .modelName(aiResponse.modelName())
                    .sourceReference(request.sourceReference())
                    .build());
            return AiSuggestionResponse.from(suggestion);
        } catch (ApiException exception) {
            aiSuggestionRepository.save(AiSuggestion.builder()
                    .project(project)
                    .suggestionType(AiSuggestionType.SPEC_ANALYSIS)
                    .status(AiSuggestionStatus.FAILED)
                    .inputText(request.specContent())
                    .sourceReference(request.sourceReference())
                    .failureReason(exception.getMessage())
                    .build());
            throw exception;
        }
    }
}
