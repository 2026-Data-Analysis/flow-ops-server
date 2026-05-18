package flowops.aiintegration.client;

import flowops.aiintegration.dto.request.AnalyzeSpecRequest;
import flowops.aiintegration.dto.request.AssistantQueryRequest;
import flowops.aiintegration.dto.request.GenerateScenarioRequest;
import flowops.aiintegration.dto.request.GenerateTestCasesRequest;
import flowops.aiintegration.dto.response.AnalyzeSpecResponse;
import flowops.aiintegration.dto.response.AssistantQueryResponse;
import flowops.aiintegration.dto.response.GenerateScenarioResponse;
import flowops.aiintegration.dto.response.GenerateTestCasesResponse;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
import flowops.integration.ai.AiAgentContracts.LogAnalysisRequest;
import flowops.integration.ai.AiAgentContracts.LogAnalysisResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioBuilderRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioBuilderResponse;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierRequest;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
public class AiWebClient implements AiClient {

    private final WebClient aiWebClient;
    private final ExternalServiceProperties properties;

    public AiWebClient(
            @Qualifier("aiApiWebClient") WebClient aiWebClient,
            ExternalServiceProperties properties
    ) {
        this.aiWebClient = aiWebClient;
        this.properties = properties;
    }

    @Override
    public AnalyzeSpecResponse analyzeSpec(AnalyzeSpecRequest request) {
        return post("/spec/analyze", request, AnalyzeSpecResponse.class);
    }

    @Override
    public GenerateTestCasesResponse generateTestCases(GenerateTestCasesRequest request) {
        return post("/test-cases/generate", request, GenerateTestCasesResponse.class);
    }

    @Override
    public GenerateScenarioResponse generateScenario(GenerateScenarioRequest request) {
        return post("/scenarios/generate", request, GenerateScenarioResponse.class);
    }

    @Override
    public AssistantQueryResponse askAssistant(AssistantQueryRequest request) {
        return post("/assistant/query", request, AssistantQueryResponse.class);
    }

    @Override
    public TestCaseGeneratorResponse generateTestCaseDrafts(TestCaseGeneratorRequest request) {
        return post("/agents/test-cases/generate", request, TestCaseGeneratorResponse.class);
    }

    @Override
    public ScenarioBuilderResponse buildScenario(ScenarioBuilderRequest request) {
        return post("/agents/scenarios/build", request, ScenarioBuilderResponse.class);
    }

    @Override
    public LogAnalysisResponse analyzeLog(LogAnalysisRequest request) {
        return post("/agents/logs/analyze", request, LogAnalysisResponse.class);
    }

    @Override
    public ErrorReportResponse generateErrorReport(ErrorReportRequest request) {
        return post("/agents/error-reports/generate", request, ErrorReportResponse.class);
    }

    @Override
    public TestStrategyClassifierResponse classifyTestStrategy(TestStrategyClassifierRequest request) {
        return post("/agents/test-strategy/classify", request, TestStrategyClassifierResponse.class);
    }

    private <T, R> R post(String path, T request, Class<R> responseType) {
        long startedAt = System.currentTimeMillis();
        log.info("AI request started. path={}, responseType={}, baseUrl={}, mockEnabled={}",
                path,
                responseType.getSimpleName(),
                properties.ai().baseUrl(),
                properties.ai().mockEnabled());
        try {
            R response = aiWebClient.post()
                    .uri(path)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("AI server request failed.")
                            .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                    .bodyToMono(responseType)
                    .timeout(Duration.ofMillis(properties.ai().readTimeoutMillis()))
                    .onErrorMap(ex -> ex instanceof ApiException ? ex
                            : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "AI server request failed."))
                    .block();
            log.info("AI request completed. path={}, responseType={}, durationMs={}",
                    path,
                    responseType.getSimpleName(),
                    System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException exception) {
            log.warn("AI request failed. path={}, responseType={}, durationMs={}, error={}",
                    path,
                    responseType.getSimpleName(),
                    System.currentTimeMillis() - startedAt,
                    exception.getMessage());
            throw exception;
        }
    }
}
