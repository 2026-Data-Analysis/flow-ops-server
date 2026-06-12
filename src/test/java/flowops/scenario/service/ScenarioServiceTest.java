package flowops.scenario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.apiinventory.service.ApiInventoryResolveRequest;
import flowops.apiinventory.service.ApiInventoryResolver;
import flowops.apiinventory.service.ResolvedApiEndpoint;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.global.config.ScenarioProperties;
import flowops.global.exception.ApiException;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateDataPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateRequest;
import flowops.integration.ai.AiAgentContracts.ScenarioGenerateResponse;
import flowops.integration.ai.AiAgentContracts.ScenarioApiInventoryPayload;
import flowops.integration.ai.AiAgentContracts.ScenarioEndpointPayload;
import flowops.integration.ai.AiAgentContracts.ExistingScenarioSummary;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.RecommendScenarioRequest;
import flowops.scenario.dto.request.ScenarioDraftSaveRequest;
import flowops.scenario.dto.request.ScenarioStepDraftRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.dto.response.ScenarioDraftSaveResponse;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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
    private ScenarioProperties scenarioProperties;
    @Mock
    private ExecutionStepLogRepository executionStepLogRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private TestCaseRepository testCaseRepository;
    @Mock
    private ApiInventoryResolver apiInventoryResolver;

    @InjectMocks
    private ScenarioService scenarioService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scenarioService, "objectMapper", new ObjectMapper().findAndRegisterModules());
        lenient().when(scenarioProperties.demoFallbackEnabled()).thenReturn(false);
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
    void saveDraftPersistsOrchestratorScenarioAndResolvesNumericApiIdAsInventory() throws Exception {
        App app = app(1L);
        ApiInventory inventory = inventory(2246L, "/orders");
        ApiEndpoint endpoint = endpoint(101L, app);
        List<ScenarioStep> savedSteps = new java.util.ArrayList<>();
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of(inventory));
        when(scenarioRepository.save(any(Scenario.class))).thenAnswer(invocation -> {
            Scenario scenario = invocation.getArgument(0);
            ReflectionTestUtils.setField(scenario, "id", 501L);
            return scenario;
        });
        when(apiInventoryResolver.resolve(any(App.class), any(ApiInventoryResolveRequest.class), any()))
                .thenReturn(Optional.of(new ResolvedApiEndpoint(
                        2246L,
                        101L,
                        "POST:/orders",
                        ApiMethod.POST,
                        "/orders",
                        endpoint,
                        inventory
                )));
        when(scenarioStepRepository.save(any(ScenarioStep.class))).thenAnswer(invocation -> {
            ScenarioStep step = invocation.getArgument(0);
            ReflectionTestUtils.setField(step, "id", 601L + savedSteps.size());
            savedSteps.add(step);
            return step;
        });
        when(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(501L)).thenReturn(savedSteps);

        ScenarioDraftSaveResponse response = scenarioService.saveDraft(1L, new ScenarioDraftSaveRequest(
                null,
                1L,
                "Order flow",
                "Generated by orchestrator",
                "HAPPY_PATH",
                "SANITY",
                List.of(new ScenarioStepDraftRequest(
                        null,
                        null,
                        null,
                        null,
                        "2246",
                        null,
                        null,
                        "Create order",
                        null,
                        null,
                        new ObjectMapper().readTree("{\"method\":\"POST\",\"path\":\"/orders\",\"body\":{\"productId\":1}}"),
                        new ObjectMapper().readTree("{\"statusCode\":201}"),
                        new ObjectMapper().readTree("{\"bodyContains\":[\"orderId\"]}"),
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                null
        ));

        assertThat(response.scenarioId()).isEqualTo(501L);
        assertThat(response.stepCount()).isEqualTo(1);
        ArgumentCaptor<ApiInventoryResolveRequest> captor = ArgumentCaptor.forClass(ApiInventoryResolveRequest.class);
        verify(apiInventoryResolver).resolve(any(App.class), captor.capture(), any());
        assertThat(captor.getValue().apiId()).isEqualTo("2246");
        assertThat(savedSteps.get(0).getApiInventory()).isEqualTo(inventory);
        verify(apiEndpointService, never()).getApiEndpointDetail(2246L);
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
    void recommendBuildsRequestWithThreeMethodPathEndpointIdsAndEmptyExistingScenarios() {
        App app = app(1L);
        ApiEndpoint login = endpoint(2249L, app, ApiMethod.POST, "/api/v1/auth/login");
        ApiEndpoint profile = endpoint(2248L, app, ApiMethod.GET, "/api/v1/users/me");
        ApiEndpoint update = endpoint(2247L, app, ApiMethod.PATCH, "/api/v1/users/me");
        when(externalServiceProperties.ai()).thenReturn(aiProperties(false));
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(apiEndpointRepository.findByAppId(1L)).thenReturn(List.of(login, profile, update));
        when(environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());
        when(testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
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
        assertThat(request.api_inventory().endpoints())
                .extracting(endpoint -> endpoint.endpoint_id())
                .containsExactly(
                        "POST:/api/v1/auth/login",
                        "GET:/api/v1/users/me",
                        "PATCH:/api/v1/users/me"
                );
        assertThat(request.existing_scenarios()).isEmpty();
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
        verify(aiClient, never()).buildScenario(any());
    }

    @Test
    void recommendTreatsNoScenariosGeneratedAsEmptyRecommendation(CapturedOutput output) {
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

        assertThat(scenarioService.recommend(new RecommendScenarioRequest(
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
                .isEmpty();
        assertThat(output.getOut())
                .contains("recommend failure diagnostic")
                .contains("NO_SCENARIOS_GENERATED")
                .contains("trace-123");
    }

    @Test
    void recommendLogsDedupDiagnosticsWhenExistingScenariosMayRemoveCandidates(CapturedOutput output) {
        App app = app(1L);
        ApiEndpoint login = endpoint(2249L, app, ApiMethod.POST, "/api/v1/auth/login");
        ApiEndpoint profile = endpoint(2248L, app, ApiMethod.GET, "/api/v1/users/me");
        Scenario existing = scenario(301L, app, "Existing auth flow");
        when(externalServiceProperties.ai()).thenReturn(aiProperties(false));
        when(appService.getApp(1L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(1L)).thenReturn(List.of());
        when(apiEndpointRepository.findByAppId(1L)).thenReturn(List.of(login, profile));
        when(environmentRepository.findFirstByAppIdOrderByCreatedAtAsc(1L)).thenReturn(Optional.empty());
        when(testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(List.of());
        when(scenarioRepository.findByAppIdOrderByUpdatedAtDesc(1L)).thenReturn(List.of(existing));
        when(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(301L))
                .thenReturn(List.of(
                        scenarioStep(existing, login, 1, "Login"),
                        scenarioStep(existing, profile, 2, "Read profile")
                ));
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class))).thenReturn(new ScenarioGenerateResponse(
                null,
                null,
                false,
                null,
                "NO_SCENARIOS_GENERATED",
                "Dedup removed every candidate",
                "trace-dedup"
        ));

        assertThat(scenarioService.recommend(new RecommendScenarioRequest(
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
                .isEmpty();

        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient).buildScenario(captor.capture());
        assertThat(captor.getValue().existing_scenarios()).hasSize(1);
        assertThat(captor.getValue().existing_scenarios().get(0).step_api_ids())
                .containsExactly("POST:/api/v1/auth/login", "GET:/api/v1/users/me");
        assertThat(output.getOut())
                .contains("dedup check")
                .contains("existingScenarioCount=1")
                .contains("sampleExistingScenarioStepApiIds")
                .contains("POST:/api/v1/auth/login")
                .contains("trace-dedup");
    }

    @Test
    void recommendKeepsLlmCallFailedAsExternalServiceError() {
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
                "LLM_CALL_FAILED",
                "Model call failed",
                "trace-llm"
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
                .hasMessageContaining("LLM_CALL_FAILED")
                .hasMessageContaining("trace-llm");
    }

    @Test
    void buildScenarioRetriesWithDemoFallbackWhenNoScenariosGenerated() {
        when(scenarioProperties.demoFallbackEnabled()).thenReturn(true);
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class)))
                .thenReturn(noScenariosResponse("NO_SCENARIOS_GENERATED", "trace-original"))
                .thenReturn(scenarioResponse("trace-fallback"));

        ScenarioGenerateResponse response = scenarioService.buildScenarioWithDemoFallback(scenarioRequest(), 1L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().fallback_used()).isTrue();
        assertThat(response.data().fallback_reason()).isEqualTo("NO_SCENARIOS_GENERATED");
        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient, org.mockito.Mockito.times(2)).buildScenario(captor.capture());
        ScenarioGenerateRequest fallback = captor.getAllValues().get(1);
        assertThat(fallback.mode()).isEqualTo("NATURAL_LANGUAGE");
        assertThat(fallback.user_intent()).contains("API");
        assertThat(fallback.max_scenarios()).isEqualTo(3);
        assertThat(fallback.max_steps_per_scenario()).isEqualTo(8);
        verify(apiEndpointService, never()).getApiEndpointDetail(any());
    }

    @Test
    void buildScenarioRetriesWithDemoFallbackForAnyRecommendModeErrorField() {
        when(scenarioProperties.demoFallbackEnabled()).thenReturn(true);
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class)))
                .thenReturn(new ScenarioGenerateResponse(
                        null,
                        null,
                        true,
                        scenarioResponse("trace-original").data(),
                        "UNKNOWN_RECOMMEND_ERROR",
                        "unexpected recommend error",
                        "trace-original"
                ))
                .thenReturn(scenarioResponse("trace-fallback"));

        ScenarioGenerateResponse response = scenarioService.buildScenarioWithDemoFallback(scenarioRecommendRequest(), 1L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().fallback_used()).isTrue();
        assertThat(response.data().fallback_reason()).isEqualTo("UNKNOWN_RECOMMEND_ERROR");
        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient, org.mockito.Mockito.times(2)).buildScenario(captor.capture());
        assertThat(captor.getAllValues().get(0).mode()).isEqualTo("RECOMMEND");
        assertThat(captor.getAllValues().get(1).mode()).isEqualTo("NATURAL_LANGUAGE");
    }

    @Test
    void buildScenarioRetriesFallbackWithoutExistingScenariosWhenDedupRemovesEverything() {
        when(scenarioProperties.demoFallbackEnabled()).thenReturn(true);
        when(scenarioProperties.demoFallbackMaxRetries()).thenReturn(2);
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class)))
                .thenReturn(noScenariosResponse("NO_SCENARIOS_GENERATED", "trace-original"))
                .thenReturn(noScenariosResponse("NO_SCENARIOS_GENERATED", "trace-fallback-1"))
                .thenReturn(scenarioResponse("trace-fallback-2"));

        ScenarioGenerateResponse response = scenarioService.buildScenarioWithDemoFallback(scenarioRequest(), 1L);

        assertThat(response.success()).isTrue();
        ArgumentCaptor<ScenarioGenerateRequest> captor = ArgumentCaptor.forClass(ScenarioGenerateRequest.class);
        verify(aiClient, org.mockito.Mockito.times(3)).buildScenario(captor.capture());
        assertThat(captor.getAllValues().get(2).existing_scenarios()).isEmpty();
    }

    @Test
    void buildScenarioReturnsMockFallbackWhenAiFallbackFailsAndInventoryCanBeMatched() {
        when(scenarioProperties.demoFallbackEnabled()).thenReturn(true);
        when(aiClient.buildScenario(any(ScenarioGenerateRequest.class)))
                .thenReturn(noScenariosResponse("LLM_CALL_FAILED", "trace-original"))
                .thenReturn(noScenariosResponse("LLM_CALL_FAILED", "trace-fallback"));

        ScenarioGenerateResponse response = scenarioService.buildScenarioWithDemoFallback(scenarioRequest(), 1L);

        assertThat(response.success()).isTrue();
        assertThat(response.data().fallback_used()).isTrue();
        assertThat(response.data().scenarios()).hasSize(1);
        assertThat(response.data().scenarios().get(0).steps()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response.data().used_endpoint_ids()).contains("POST:/projects", "POST:/mates/{mateId}/likes");
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
        return endpoint(id, app, ApiMethod.POST, "/orders");
    }

    private ApiEndpoint endpoint(Long id, App app, ApiMethod method, String path) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(method)
                .path(path)
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", id);
        return endpoint;
    }

    private ApiInventory inventory(Long id, String path) {
        ApiInventory inventory = ApiInventory.builder()
                .method(ApiHttpMethod.POST)
                .endpointPath(path)
                .operationId("createOrder")
                .domainTag("ORDER")
                .sourceType(ApiInventorySource.OPENAPI)
                .status(ApiInventoryStatus.ACTIVE)
                .authRequired(false)
                .build();
        ReflectionTestUtils.setField(inventory, "id", id);
        return inventory;
    }

    private Scenario scenario(Long id, App app, String name) {
        Scenario scenario = Scenario.builder()
                .app(app)
                .name(name)
                .type(ScenarioType.HAPPY_PATH)
                .source(ScenarioSource.AI)
                .build();
        ReflectionTestUtils.setField(scenario, "id", id);
        return scenario;
    }

    private ScenarioStep scenarioStep(Scenario scenario, ApiEndpoint endpoint, int order, String label) {
        ScenarioStep step = ScenarioStep.builder()
                .scenario(scenario)
                .apiEndpoint(endpoint)
                .stepOrder(order)
                .label(label)
                .build();
        ReflectionTestUtils.setField(step, "id", 400L + order);
        return step;
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

    private ScenarioGenerateRequest scenarioRequest() {
        return scenarioRequest("NATURAL_LANGUAGE");
    }

    private ScenarioGenerateRequest scenarioRecommendRequest() {
        return scenarioRequest("RECOMMEND");
    }

    private ScenarioGenerateRequest scenarioRequest(String mode) {
        return new ScenarioGenerateRequest(
                "1",
                mode,
                "사용자 요청",
                new ScenarioApiInventoryPayload(
                        "1",
                        List.of(
                                endpointPayload("POST:/projects", "POST", "/projects", "프로젝트 생성"),
                                endpointPayload("GET:/mates", "GET", "/mates", "메이트 후보 조회"),
                                endpointPayload("POST:/mates/{mateId}/likes", "POST", "/mates/{mateId}/likes", "메이트 좋아요"),
                                endpointPayload("GET:/matches", "GET", "/matches", "매칭 조회")
                        )
                ),
                null,
                List.of(),
                List.of(new ExistingScenarioSummary("Existing", List.of("POST:/projects"))),
                null,
                null
        );
    }

    private ScenarioEndpointPayload endpointPayload(String endpointId, String method, String path, String summary) {
        return new ScenarioEndpointPayload(
                endpointId,
                path,
                method,
                summary,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private ScenarioGenerateResponse noScenariosResponse(String errorCode, String traceId) {
        return new ScenarioGenerateResponse(
                null,
                null,
                false,
                null,
                errorCode,
                errorCode,
                traceId
        );
    }

    private ScenarioGenerateResponse scenarioResponse(String traceId) {
        return new ScenarioGenerateResponse(
                null,
                null,
                true,
                new ScenarioGenerateDataPayload(
                        List.of(new flowops.integration.ai.AiAgentContracts.ScenarioPayload(
                                "scenario-1",
                                "Fallback scenario",
                                "demo",
                                "HAPPY_PATH",
                                "SANITY",
                                List.of(new flowops.integration.ai.AiAgentContracts.ScenarioStepPayload(
                                        "step-1",
                                        "step_1",
                                        1,
                                        null,
                                        "POST:/projects",
                                        "POST:/projects",
                                        "Create project",
                                        "Create project",
                                        null,
                                        "HAPPY_PATH",
                                        "SANITY",
                                        null,
                                        null,
                                        null,
                                        "/projects",
                                        "POST",
                                        null,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null,
                                        201,
                                        List.of()
                                )),
                                null
                        )),
                        List.of("POST:/projects")
                ),
                null,
                null,
                traceId
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
