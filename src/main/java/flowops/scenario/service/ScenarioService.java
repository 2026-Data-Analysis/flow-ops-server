package flowops.scenario.service;

import flowops.api.service.ApiEndpointService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.ReorderScenarioStepsRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.request.UpdateScenarioRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioRecommendationResponse;
import flowops.scenario.dto.response.ScenarioStepResponse;
import flowops.scenario.dto.response.ScenarioSummaryResponse;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여러 API를 묶은 시나리오의 추천, 생성, 수정, 단계 재정렬을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class ScenarioService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioStepRepository scenarioStepRepository;
    private final AppService appService;
    private final ApiEndpointService apiEndpointService;

    @Transactional
    public ScenarioDetailResponse create(CreateScenarioRequest request) {
        App app = appService.getApp(request.appId());
        Scenario scenario = scenarioRepository.save(Scenario.builder()
                .app(app)
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .recommendationReason(request.recommendationReason())
                .source(request.source())
                .build());
        List<ScenarioStepResponse> steps = replaceSteps(scenario, request.steps());
        return ScenarioDetailResponse.of(scenario, steps);
    }

    @Transactional(readOnly = true)
    public List<ScenarioSummaryResponse> listByApp(Long appId) {
        appService.getApp(appId);
        return scenarioRepository.findByAppIdOrderByUpdatedAtDesc(appId)
                .stream()
                .map(scenario -> ScenarioSummaryResponse.from(
                        scenario,
                        scenarioStepRepository.countByScenarioId(scenario.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScenarioDetailResponse getDetail(Long scenarioId) {
        Scenario scenario = getScenario(scenarioId);
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional
    public ScenarioDetailResponse update(Long scenarioId, UpdateScenarioRequest request) {
        Scenario scenario = getScenario(scenarioId);
        scenario.update(
                request.name(),
                request.description(),
                request.type(),
                request.recommendationReason()
        );
        if (request.steps() != null) {
            scenarioStepRepository.deleteAll(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId));
            return ScenarioDetailResponse.of(scenario, replaceSteps(scenario, request.steps()));
        }
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional
    public ScenarioDetailResponse reorderSteps(Long scenarioId, ReorderScenarioStepsRequest request) {
        Scenario scenario = getScenario(scenarioId);
        List<ScenarioStep> steps = scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId);
        for (ReorderScenarioStepsRequest.ScenarioStepOrderItem item : request.steps()) {
            ScenarioStep step = steps.stream()
                    .filter(candidate -> candidate.getId().equals(item.stepId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "시나리오 단계를 찾을 수 없습니다."));
            step.update(
                    item.stepOrder(),
                    step.getApiEndpoint(),
                    step.getLabel(),
                    step.getRequestConfig(),
                    step.getExtractRules(),
                    step.getValidationRules()
            );
        }
        return ScenarioDetailResponse.of(scenario, getStepResponses(scenarioId));
    }

    @Transactional(readOnly = true)
    public List<ScenarioRecommendationResponse> recommend() {
        return List.of(
                new ScenarioRecommendationResponse("Critical checkout flow", flowops.scenario.domain.entity.ScenarioType.HAPPY_PATH, "Covers a high-value multi-endpoint business path."),
                new ScenarioRecommendationResponse("Validation guard rails", flowops.scenario.domain.entity.ScenarioType.EDGE_CASE, "Focuses on required-field and malformed-input failures."),
                new ScenarioRecommendationResponse("Recovery after dependency failure", flowops.scenario.domain.entity.ScenarioType.FAILURE_RECOVERY, "Models retry and fallback behavior after an upstream error.")
        );
    }

    @Transactional(readOnly = true)
    public Scenario getScenario(Long scenarioId) {
        return scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "시나리오를 찾을 수 없습니다."));
    }

    private List<ScenarioStepResponse> replaceSteps(Scenario scenario, List<ScenarioStepRequest> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<ScenarioStepResponse> responses = new ArrayList<>();
        for (ScenarioStepRequest step : steps) {
            ScenarioStep saved = scenarioStepRepository.save(ScenarioStep.builder()
                    .scenario(scenario)
                    .stepOrder(step.stepOrder())
                    .apiEndpoint(apiEndpointService.getApiEndpoint(step.apiId()))
                    .label(step.label())
                    .requestConfig(step.requestConfig())
                    .extractRules(step.extractRules())
                    .validationRules(step.validationRules())
                    .build());
            responses.add(ScenarioStepResponse.from(saved));
        }
        return responses.stream()
                .sorted(java.util.Comparator.comparing(ScenarioStepResponse::stepOrder))
                .toList();
    }

    private List<ScenarioStepResponse> getStepResponses(Long scenarioId) {
        return scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(scenarioId)
                .stream()
                .map(ScenarioStepResponse::from)
                .toList();
    }
}
