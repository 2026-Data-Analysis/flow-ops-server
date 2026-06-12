package flowops.aiintegration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.aiintegration.dto.request.OrchestratorDispatchRequest;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.IncidentAgentData;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse.ScenarioAgentData;
import flowops.app.service.AppService;
import flowops.api.service.ApiEndpointService;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeDataPayload;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeRequest;
import flowops.integration.ai.AiAgentContracts.IncidentAnalyzeResponse;
import flowops.integration.ai.AiAgentContracts.IncidentRootCausePayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateDataPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioStepPayload;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private AiClient aiClient;
    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private ScenarioStepRepository scenarioStepRepository;
    @Mock
    private AppService appService;
    @Mock
    private ApiEndpointService apiEndpointService;
    @Mock
    private TestGenerationRepository testGenerationRepository;
    @Mock
    private TestGenerationApiSelectionRepository selectionRepository;
    @Mock
    private GeneratedTestCaseDraftRepository draftRepository;

    @Test
    void rawLogDispatchesToIncidentAndNormalizesSnakeCaseResponse() throws Exception {
        when(aiClient.analyzeIncident(any(IncidentAnalyzeRequest.class))).thenReturn(new IncidentAnalyzeResponse(
                true,
                new IncidentAnalyzeDataPayload(
                        List.of(new IncidentRootCausePayload(
                                "Null order stock",
                                "HIGH",
                                "Guard missing inventory rows",
                                List.of("OrderService.java:87")
                        )),
                        "Internal report",
                        "External notice"
                ),
                null,
                null,
                "ai-trace"
        ));

        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "ecommerce-backend",
                "주문 서비스에서 에러가 계속 나고 있어. 로그 분석해줘.",
                json("""
                        {
                          "service_name": "order-service",
                          "occurred_at": "2025-05-29T14:32:00Z",
                          "raw_log": "ERROR NullPointerException"
                        }
                        """)
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.data().dispatchedAgents()).containsExactly("incident");
        assertThat(response.data().summary()).contains("원인 후보 1건");
        IncidentAgentData data = (IncidentAgentData) response.data().agentResults().get(0).data();
        assertThat(data.rootCauses()).hasSize(1);
        assertThat(data.internalReport()).isEqualTo("Internal report");
        JsonNode serialized = objectMapper.valueToTree(data);
        assertThat(serialized.has("root_causes")).isTrue();
        assertThat(serialized.has("internal_report")).isTrue();
        assertThat(serialized.has("external_notice")).isTrue();

        ArgumentCaptor<IncidentAnalyzeRequest> captor = ArgumentCaptor.forClass(IncidentAnalyzeRequest.class);
        verify(aiClient).analyzeIncident(captor.capture());
        assertThat(captor.getValue().project_id()).isEqualTo("ecommerce-backend");
        assertThat(captor.getValue().raw_log()).contains("NullPointerException");
    }

    @Test
    void incidentPromptWithoutRawLogReturnsMissingRawLog() {
        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "ecommerce-backend",
                "로그 분석해줘",
                json("""
                        {"service_name": "order-service"}
                        """)
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MISSING_RAW_LOG");
        verify(aiClient, never()).analyzeIncident(any());
    }

    @Test
    void scenarioPromptBuildsNaturalLanguageRequestAndNormalizesNewStepShape() {
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(new ScenarioGenerateResponse(
                "req-1",
                "gen-1",
                true,
                new ScenarioGenerateDataPayload(
                        List.of(new ScenarioPayload(
                                "scenario-1",
                                "Signup to order",
                                "Flow",
                                "E2E",
                                "REGRESSION",
                                List.of(new ScenarioStepPayload(
                                        null,
                                        null,
                                        1,
                                        null,
                                        null,
                                        "POST:/api/v1/auth/signup",
                                        null,
                                        "신규 회원가입",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        json("""
                                                {"body":{"email":"a@b.com"},"pathParams":{"tenantId":"t1"},"queryParams":{"dryRun":false}}
                                                """),
                                        json("""
                                                {"statusCode":201}
                                                """),
                                        json("""
                                                {"assertions":["status == 201"]}
                                                """),
                                        false,
                                        null,
                                        null,
                                        null,
                                        null
                                )),
                                null
                        )),
                        List.of("POST:/api/v1/auth/signup")
                ),
                null,
                null,
                "trace-ai"
        ));

        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "회원가입 후 로그인하고 주문 흐름을 시나리오로 만들어줘.",
                scenarioContext()
        ));

        assertThat(response.success()).isTrue();
        assertThat(response.data().dispatchedAgents()).containsExactly("scenario");
        assertThat(response.data().summary()).contains("1개 시나리오 생성됨");
        ScenarioAgentData data = (ScenarioAgentData) response.data().agentResults().get(0).data();
        assertThat(data.usedEndpointIds()).containsExactly("POST:/api/v1/auth/signup");
        assertThat(data.scenarios().get(0).steps().get(0).endpointId()).isEqualTo("POST:/api/v1/auth/signup");
        assertThat(data.scenarios().get(0).steps().get(0).name()).isEqualTo("신규 회원가입");
        assertThat(data.scenarios().get(0).steps().get(0).payload().get("email").asText()).isEqualTo("a@b.com");
        assertThat(data.scenarios().get(0).steps().get(0).params().get("tenantId").asText()).isEqualTo("t1");
        assertThat(data.scenarios().get(0).steps().get(0).expectedStatusCode()).isEqualTo(201);

        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient).buildScenario(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo("NATURAL_LANGUAGE");
        assertThat(captor.getValue().user_intent()).isEqualTo("회원가입 후 로그인하고 주문 흐름을 시나리오로 만들어줘.");
        assertThat(captor.getValue().max_scenarios()).isEqualTo(3);
    }

    @Test
    void scenarioPromptNormalizesLegacyStepShape() {
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(new ScenarioGenerateResponse(
                "req-1",
                "gen-1",
                true,
                new ScenarioGenerateDataPayload(
                        List.of(new ScenarioPayload(
                                "scenario-1",
                                "Signup",
                                null,
                                "E2E",
                                "REGRESSION",
                                List.of(new ScenarioStepPayload(
                                        null,
                                        null,
                                        1,
                                        null,
                                        "POST:/api/v1/auth/signup",
                                        null,
                                        "신규 회원가입",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        json("""
                                                {"email":"a@b.com"}
                                                """),
                                        json("""
                                                {"tenantId":"t1"}
                                                """),
                                        201,
                                        List.of("status == 201")
                                )),
                                null
                        )),
                        List.of("POST:/api/v1/auth/signup")
                ),
                null,
                null,
                "trace-ai"
        ));

        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "회원가입 시나리오 만들어줘.",
                scenarioContext()
        ));

        ScenarioAgentData data = (ScenarioAgentData) response.data().agentResults().get(0).data();
        assertThat(data.scenarios().get(0).steps().get(0).endpointId()).isEqualTo("POST:/api/v1/auth/signup");
        assertThat(data.scenarios().get(0).steps().get(0).name()).isEqualTo("신규 회원가입");
        assertThat(data.scenarios().get(0).steps().get(0).payload().get("email").asText()).isEqualTo("a@b.com");
    }

    @Test
    void scenarioValidationErrorsAreReturnedBeforeCallingAgent() {
        OrchestratorDispatchResponse missingInventory = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "E2E 시나리오 만들어줘",
                json("{}")
        ));
        OrchestratorDispatchResponse emptyInventory = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "E2E 시나리오 만들어줘",
                json("""
                        {"api_inventory":{"project_id":"1","endpoints":[]}}
                        """)
        ));

        assertThat(missingInventory.errorCode()).isEqualTo("MISSING_API_INVENTORY");
        assertThat(emptyInventory.errorCode()).isEqualTo("EMPTY_API_INVENTORY");
        verify(aiClient, never()).buildScenario(any());
    }

    @Test
    void scenarioNoScenariosGeneratedStaysInAgentResult() {
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(new ScenarioGenerateResponse(
                "req-1",
                "gen-1",
                false,
                null,
                "NO_SCENARIOS_GENERATED",
                "조건에 맞는 시나리오를 생성하지 못했습니다.",
                "trace-ai"
        ));

        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "E2E 시나리오 만들어줘",
                scenarioContext()
        ));

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("NO_SCENARIOS_GENERATED");
        assertThat(response.data().agentResults().get(0).agentType()).isEqualTo("scenario");
        assertThat(response.data().agentResults().get(0).success()).isFalse();
        assertThat(response.data().agentResults().get(0).errorMessage()).isEqualTo("조건에 맞는 시나리오를 생성하지 못했습니다.");
    }

    @Test
    void rawLogTakesPriorityOverScenarioPrompt() {
        when(aiClient.analyzeIncident(any(IncidentAnalyzeRequest.class))).thenReturn(new IncidentAnalyzeResponse(
                true,
                new IncidentAnalyzeDataPayload(List.of(), "", ""),
                null,
                null,
                "trace-ai"
        ));

        OrchestratorDispatchResponse response = service().dispatch(new OrchestratorDispatchRequest(
                "1",
                "E2E 시나리오도 만들고 로그 분석도 해줘",
                json("""
                        {
                          "raw_log": "ERROR",
                          "api_inventory": {
                            "project_id": "1",
                            "endpoints": [{"endpoint_id":"GET:/orders","method":"GET","path":"/orders"}]
                          }
                        }
                        """)
        ));

        assertThat(response.data().dispatchedAgents()).containsExactly("incident");
        verify(aiClient).analyzeIncident(any());
        verify(aiClient, never()).buildScenario(any());
    }

    private OrchestratorService service() {
        return new OrchestratorService(
                aiClient,
                scenarioRepository,
                scenarioStepRepository,
                appService,
                apiEndpointService,
                testGenerationRepository,
                selectionRepository,
                draftRepository,
                objectMapper
        );
    }

    private JsonNode scenarioContext() {
        return json("""
                {
                  "api_inventory": {
                    "project_id": "1",
                    "endpoints": [
                      {"endpoint_id":"POST:/api/v1/auth/signup","method":"POST","path":"/api/v1/auth/signup"}
                    ]
                  }
                }
                """);
    }

    private JsonNode json(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
    }
}
