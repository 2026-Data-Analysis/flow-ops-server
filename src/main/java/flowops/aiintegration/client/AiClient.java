package flowops.aiintegration.client;

import flowops.aiintegration.dto.request.AnalyzeSpecRequest;
import flowops.aiintegration.dto.response.AnalyzeSpecResponse;
import flowops.integration.ai.AiAgentContracts.ErrorReportRequest;
import flowops.integration.ai.AiAgentContracts.ErrorReportResponse;
import flowops.integration.ai.AiAgentContracts.LogAnalysisRequest;
import flowops.integration.ai.AiAgentContracts.LogAnalysisResponse;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeRequest;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeResponse;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatRequest;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierRequest;
import flowops.integration.ai.AiAgentContracts.TestStrategyClassifierResponse;

public interface AiClient {
    AnalyzeSpecResponse analyzeSpec(AnalyzeSpecRequest request);

    TestCaseGeneratorResponse generateTestCaseDrafts(TestCaseGeneratorRequest request);

    ScenarioGenerateResponse buildScenario(ScenarioGenerateRequest request);

    LogAnalysisResponse analyzeLog(LogAnalysisRequest request);

    ErrorReportResponse generateErrorReport(ErrorReportRequest request);

    TestStrategyClassifierResponse classifyTestStrategy(TestStrategyClassifierRequest request);

    OrchestratorChatResponse chatWithOrchestrator(OrchestratorChatRequest request);

    IncidentAnalyzeResponse analyzeIncident(IncidentAnalyzeRequest request);
}
