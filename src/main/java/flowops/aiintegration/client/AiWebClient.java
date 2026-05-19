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
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
        return post("/api/v1/agents/testcase/generate", request, TestCaseGeneratorResponse.class);
    }

    @Override
    public ScenarioGenerateResponse buildScenario(ScenarioGenerateRequest request) {
        return post("/v1/agents/scenario/generate", request, ScenarioGenerateResponse.class);
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
            Mono<R> responseMono = aiWebClient.post()
                    .uri(path)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new ApiException(
                                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                                    aiErrorMessage(path, clientResponse.statusCode(), body)
                            )))
                    .bodyToMono(responseType);
            if (properties.ai().readTimeoutMillis() > 0) {
                responseMono = responseMono.timeout(Duration.ofMillis(properties.ai().readTimeoutMillis()));
            }
            responseMono = responseMono.onErrorMap(ex -> ex instanceof ApiException ? ex
                    : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, aiTransportErrorMessage(path, ex), ex));
            R response = responseMono.block();
            log.info("AI request completed. path={}, responseType={}, durationMs={}",
                    path,
                    responseType.getSimpleName(),
                    System.currentTimeMillis() - startedAt);
            return response;
        } catch (RuntimeException exception) {
            log.warn("AI request failed. path={}, responseType={}, baseUrl={}, readTimeoutMillis={}, durationMs={}, errorType={}, error={}",
                    path,
                    responseType.getSimpleName(),
                    properties.ai().baseUrl(),
                    properties.ai().readTimeoutMillis(),
                    System.currentTimeMillis() - startedAt,
                    rootCause(exception).getClass().getName(),
                    exception.getMessage());
            if (exception instanceof WebClientResponseException responseException) {
                log.warn("AI response error body. path={}, status={}, body={}",
                        path,
                        responseException.getStatusCode().value(),
                        responseException.getResponseBodyAsString());
            }
            throw exception;
        }
    }

    private String aiErrorMessage(String path, HttpStatusCode statusCode, String body) {
        return "AI server request failed. path=%s, status=%s, body=%s"
                .formatted(path, statusCode.value(), compact(body));
    }

    private String aiTransportErrorMessage(String path, Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        return "AI server request failed. path=%s, baseUrl=%s, readTimeoutMillis=%d, errorType=%s, error=%s"
                .formatted(
                        path,
                        properties.ai().baseUrl(),
                        properties.ai().readTimeoutMillis(),
                        rootCause.getClass().getSimpleName(),
                        compact(rootCause.getMessage())
                );
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 2000 ? compacted.substring(0, 2000) + "..." : compacted;
    }
}
