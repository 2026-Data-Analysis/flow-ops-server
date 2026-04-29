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
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
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

    private <T, R> R post(String path, T request, Class<R> responseType) {
        return aiWebClient.post()
                .uri(path)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("AI 서버 요청에 실패했습니다.")
                        .map(body -> new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, body)))
                .bodyToMono(responseType)
                .timeout(Duration.ofMillis(properties.ai().readTimeoutMillis()))
                .onErrorMap(ex -> ex instanceof ApiException ? ex
                        : new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "AI 서버 요청에 실패했습니다."))
                .block();
    }
}
