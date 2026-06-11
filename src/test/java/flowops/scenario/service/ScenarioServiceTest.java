package flowops.scenario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.exception.ApiException;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateDataPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.RecommendScenarioRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private ScenarioStepRepository scenarioStepRepository;
    @Mock
    private AppService appService;
    @Mock
    private ApiEndpointService apiEndpointService;
    @Mock
    private ApiEndpointRepository apiEndpointRepository;
    @Mock
    private ApiInventoryRepository apiInventoryRepository;
    @Mock
    private AiClient aiClient;
    @Mock
    private ExternalServiceProperties externalServiceProperties;
    @Mock
    private ExecutionStepLogRepository executionStepLogRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private TestCaseRepository testCaseRepository;

    @InjectMocks
    private ScenarioService scenarioService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scenarioService, "objectMapper", new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createPersistsEnvironmentFromRequest() {
        App app = app(1L);
        Environment environment = environment(3L, app);
        ApiEndpoint endpoint = endpoint(101L, app);
        when(appService.getApp(1L)).thenReturn(app);
        when(environmentRepository.findById(3L)).thenReturn(Optional.of(environment));
        when(apiInventoryRepository.findById(101L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(101L)).thenReturn(endpoint);
        when(scenarioRepository.save(any(Scenario.class))).thenAnswer(invocation -> {
            Scenario scenario = invocation.getArgument(0);
            ReflectionTestUtils.setField(scenario, "id", 501L);
            return scenario;
        });
        when(scenarioStepRepository.save(any(ScenarioStep.class))).thenAnswer(invocation -> {
            ScenarioStep step = invocation.getArgument(0);
            ReflectionTestUtils.setField(step, "id", 601L);
            return step;
        });

        ScenarioDetailResponse response = scenarioService.create(new CreateScenarioRequest(
                1L,
                3L,
                "Order flow",
                null,
                ScenarioType.HAPPY_PATH,
                null,
                ScenarioSource.AI,
                List.of(new ScenarioStepRequest(
                        null,
                        1,
                        101L,
                        "Create order",
                        "step-1",
                        "step_1",
                        null,
                        "HAPPY_PATH",
                        "REGRESSION",
                        "CUSTOMER",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null
                ))
        ));

        assertThat(response.environmentId()).isEqualTo(3L);
        assertThat(response.steps()).hasSize(1);
    }

    @Test
    void recommendBuildsRecommendRequestWithoutUserIntentAndWithExistingCoverageContext() {
        App app = app(1L);
        ApiEndpoint endpoint = endpoint(101L, app);
        TestCase testCase = testCase(201L, app, endpoint);
        when(externalServiceProperties.ai()).thenReturn(aiProperties(false));
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(apiEndpointRepository.findByAppId(1L)).thenReturn(List.of(endpoint));
        when(environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());
        when(testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of(testCase));
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(successResponse());

        scenarioService.recommend(new RecommendScenarioRequest(
                1L,
                null,
                null,
                ScenarioType.HAPPY_PATH,
                null,
                null,
                "qa",
                List.of(),
                null,
                null
        ));

        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient).buildScenario(captor.capture());
        ScenarioGenerateRequest request = captor.getValue();
        assertThat(request.mode()).isEqualTo("RECOMMEND");
        assertThat(request.user_intent()).isNull();
        assertThat(request.api_inventory().endpoints()).hasSize(1);
        assertThat(request.api_inventory().endpoints().get(0).endpoint_id()).isEqualTo("POST:/orders");
        assertThat(request.existing_test_cases()).hasSize(1);
        assertThat(request.existing_test_cases().get(0).apiId()).isEqualTo("POST:/orders");
        assertThat(request.existing_scenarios()).isEmpty();
        assertThat(request.max_scenarios()).isEqualTo(3);
        assertThat(request.max_steps_per_scenario()).isNull();
    }

    @Test
    void recommendRejectsEmptyApiInventoryBeforeCallingAi() {
        App app = app(1L);
        when(externalServiceProperties.ai()).thenReturn(aiProperties(false));
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(apiEndpointRepository.findByAppId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> scenarioService.recommend(new RecommendScenarioRequest(
                1L,
                null,
                null,
                ScenarioType.HAPPY_PATH,
                null,
                null,
                "qa",
                List.of(),
                null,
                null
        )))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("api_inventory.endpoints");
    }

    @Test
    void recommendTreatsAiSuccessFalseAsExternalServiceError() {
        App app = app(1L);
        ApiEndpoint endpoint = endpoint(101L, app);
        when(externalServiceProperties.ai()).thenReturn(aiProperties(false));
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(apiEndpointRepository.findByAppId(1L)).thenReturn(List.of(endpoint));
        when(environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());
        when(testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(new ScenarioGenerateResponse(
                null,
                null,
                false,
                null,
                "NO_SCENARIOS_GENERATED",
                "No recommendable scenario found",
                "trace-123"
        ));

        assertThatThrownBy(() -> scenarioService.recommend(new RecommendScenarioRequest(
                1L,
                null,
                null,
                ScenarioType.HAPPY_PATH,
                null,
                null,
                "qa",
                List.of(),
                null,
                null
        )))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("NO_SCENARIOS_GENERATED")
                .hasMessageContaining("trace-123");
    }

    private App app(Long id) {
        App app = App.builder()
                .name("FlowOps")
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private Environment environment(Long id, App app) {
        Environment environment = Environment.builder()
                .app(app)
                .name("dev")
                .baseUrl("https://api.example.com")
                .build();
        ReflectionTestUtils.setField(environment, "id", id);
        return environment;
    }

    private ApiEndpoint endpoint(Long id, App app) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(ApiMethod.POST)
                .path("/orders")
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", id);
        return endpoint;
    }

    private TestCase testCase(Long id, App app, ApiEndpoint endpoint) {
        TestCase testCase = TestCase.builder()
                .app(app)
                .apiEndpoint(endpoint)
                .name("Create order")
                .description("Existing saved order creation test")
                .type(TestCaseType.HAPPY_PATH)
                .testLevel(TestLevel.REGRESSION)
                .source(TestCaseSource.AUTO)
                .requestSpec("{\"method\":\"POST\"}")
                .expectedSpec("{\"statusCode\":201}")
                .assertionSpec("{\"bodyContains\":[\"orderId\"]}")
                .active(true)
                .version(1)
                .build();
        ReflectionTestUtils.setField(testCase, "id", id);
        return testCase;
    }

    private ScenarioGenerateResponse successResponse() {
        return new ScenarioGenerateResponse(
                null,
                null,
                true,
                new ScenarioGenerateDataPayload(List.of(), List.of()),
                null,
                null,
                "trace-ok"
        );
    }

    private ExternalServiceProperties.Ai aiProperties(boolean mockEnabled) {
        return new ExternalServiceProperties.Ai(
                "http://localhost:8000",
                null,
                mockEnabled,
                1000,
                1000
        );
    }
}
