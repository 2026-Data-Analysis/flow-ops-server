package flowops.aiintegration.client;

import flowops.aiintegration.dto.request.AnalyzeSpecRequest;
import flowops.aiintegration.dto.request.AssistantQueryRequest;
import flowops.aiintegration.dto.request.GenerateScenarioRequest;
import flowops.aiintegration.dto.request.GenerateTestCasesRequest;
import flowops.aiintegration.dto.response.AnalyzeSpecResponse;
import flowops.aiintegration.dto.response.AssistantQueryResponse;
import flowops.aiintegration.dto.response.GenerateScenarioResponse;
import flowops.aiintegration.dto.response.GenerateTestCasesResponse;

public interface AiClient {
    AnalyzeSpecResponse analyzeSpec(AnalyzeSpecRequest request);

    GenerateTestCasesResponse generateTestCases(GenerateTestCasesRequest request);

    GenerateScenarioResponse generateScenario(GenerateScenarioRequest request);

    AssistantQueryResponse askAssistant(AssistantQueryRequest request);
}
