package flowops.execution.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.AuthType;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.ExecutionMode;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStatus;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.execution.domain.entity.ExecutionStepStatus;
import flowops.execution.domain.entity.ExecutionTriggerSource;
import flowops.execution.domain.entity.ExecutionType;
import flowops.execution.domain.entity.TestValidationResult;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.execution.repository.TestValidationResultRepository;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;

class HttpExecutionEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private HttpExecutionEngine engine;
    private ExecutionStepLogRepository stepLogRepository;
    private TestValidationResultRepository validationResultRepository;
    private ScenarioStepRepository scenarioStepRepository;
    private ApiEndpointService apiEndpointService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        stepLogRepository = mock(ExecutionStepLogRepository.class);
        validationResultRepository = mock(TestValidationResultRepository.class);
        scenarioStepRepository = mock(ScenarioStepRepository.class);
        apiEndpointService = mock(ApiEndpointService.class);
        when(stepLogRepository.save(any(ExecutionStepLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(validationResultRepository.save(any(TestValidationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        engine = new HttpExecutionEngine(
                stepLogRepository,
                validationResultRepository,
                scenarioStepRepository,
                mock(TestCaseRepository.class),
                apiEndpointService,
                WebClient.builder(),
                objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void executeUsesEnvironmentBaseUrlHeadersAndExpectedBody() throws Exception {
        server.createContext("/orders", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            assertThat(exchange.getRequestHeaders().getFirst("X-Env")).isEqualTo("qa");
            assertThat(new String(requestBody, StandardCharsets.UTF_8)).contains("\"item\":\"book\"");
            writeResponse(exchange, 201, "{\"id\":7,\"status\":\"created\",\"ignored\":true}");
        });
        server.start();

        ExecutionStepLog log = engine.execute(
                execution(environment("{\"X-Env\":\"qa\"}")),
                null,
                null,
                api(ApiMethod.POST, "/orders"),
                "create order",
                new HttpExecutionEngine.RequestDefinition(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        objectMapper.readTree("{\"item\":\"book\"}"),
                        objectMapper
                ),
                new HttpExecutionEngine.ExpectedDefinition(
                        201,
                        objectMapper.readTree("{\"status\":\"created\"}")
                )
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.SUCCESS);
        assertThat(log.getResponseCode()).isEqualTo(201);
        assertThat(log.getRequestBody()).contains("\"item\":\"book\"");
        ArgumentCaptor<TestValidationResult> captor = ArgumentCaptor.forClass(TestValidationResult.class);
        org.mockito.Mockito.verify(validationResultRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TestValidationResult::getAssertionName)
                .containsExactly("HTTP status", "Response body");
        assertThat(captor.getAllValues()).allMatch(TestValidationResult::isPassed);
    }

    @Test
    void executeFallsBackToResponseStatusWhenNoExpectedValueExists() {
        server.createContext("/fail", exchange -> writeResponse(exchange, 500, "{\"error\":\"failed\"}"));
        server.start();

        ExecutionStepLog log = engine.execute(
                execution(environment("{}")),
                null,
                null,
                api(ApiMethod.GET, "/fail"),
                "fail",
                HttpExecutionEngine.RequestDefinition.empty(objectMapper),
                HttpExecutionEngine.ExpectedDefinition.responseStatus()
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("Expected HTTP status 2xx");
    }

    @Test
    void executeReplacesMissingPathParamWithRunnableSampleValue() {
        server.createContext("/users/1", exchange -> writeResponse(exchange, 200, "{\"id\":1}"));
        server.start();

        ExecutionStepLog log = engine.execute(
                execution(environment("{}")),
                null,
                null,
                api(ApiMethod.GET, "/users/{userId}"),
                "get user",
                HttpExecutionEngine.RequestDefinition.empty(objectMapper),
                HttpExecutionEngine.ExpectedDefinition.responseStatus()
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.SUCCESS);
        assertThat(log.getResponseCode()).isEqualTo(200);
        assertThat(log.getPath()).isEqualTo("/users/1");
    }

    @Test
    void executeScenarioChainsExtractedValueIntoLaterPathParam() {
        server.createContext("/sessions", exchange -> writeResponse(exchange, 200, "{\"sessionId\":42}"));
        server.createContext("/sessions/42/orders", exchange -> writeResponse(exchange, 201, "{\"ok\":true}"));
        server.start();

        Scenario scenario = Scenario.builder()
                .app(App.builder().name("FlowOps").build())
                .name("session order")
                .type(ScenarioType.HAPPY_PATH)
                .source(ScenarioSource.CUSTOM)
                .build();
        ScenarioStep first = ScenarioStep.builder()
                .scenario(scenario)
                .stepOrder(1)
                .apiEndpoint(api(ApiMethod.POST, "/sessions"))
                .label("create session")
                .extractRules("{\"name\":\"sessionId\",\"jsonPath\":\"$.sessionId\"}")
                .validationRules("{\"status\":200}")
                .build();
        ScenarioStep second = ScenarioStep.builder()
                .scenario(scenario)
                .stepOrder(2)
                .apiEndpoint(api(ApiMethod.POST, "/sessions/{sessionId}/orders"))
                .label("create order")
                .requestConfig("{\"params\":{\"sessionId\":\"{{sessionId}}\"}}")
                .validationRules("{\"status\":201}")
                .build();
        when(scenarioStepRepository.findByScenarioIdOrderByStepOrderAsc(10L)).thenReturn(List.of(first, second));

        List<ExecutionStepLog> logs = engine.executeScenario(execution(environment("{}")), 10L);

        assertThat(logs).hasSize(2);
        assertThat(logs).extracting(ExecutionStepLog::getPath)
                .containsExactly("/sessions", "/sessions/42/orders");
        assertThat(logs).extracting(ExecutionStepLog::getResponseCode)
                .containsExactly(200, 201);
        assertThat(logs).extracting(ExecutionStepLog::getStatus)
                .containsExactly(ExecutionStepStatus.SUCCESS, ExecutionStepStatus.SUCCESS);
    }

    @Test
    void failedConnectionStoresNoResponseInsteadOfSyntheticZeroStatus() {
        ExecutionStepLog log = engine.execute(
                execution(environment("{}", "http://localhost:1")),
                null,
                null,
                api(ApiMethod.GET, "/missing"),
                "missing",
                HttpExecutionEngine.RequestDefinition.empty(objectMapper),
                HttpExecutionEngine.ExpectedDefinition.responseStatus()
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.FAILED);
        assertThat(log.getResponseCode()).isNull();
        assertThat(log.getErrorMessage()).contains("no HTTP response");
    }

    @Test
    void executeUsesRequestSpecEndpointAndMethodOverride() {
        server.createContext("/apps/invalid/scenarios", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            writeResponse(exchange, 400, "{\"code\":\"COMMON-400\"}");
        });
        server.start();

        ExecutionStepLog log = engine.execute(
                execution(environment("{}")),
                null,
                null,
                api(ApiMethod.POST, "/apps/{appId}/scenarios"),
                "invalid app id",
                new HttpExecutionEngine.RequestDefinition(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        "/apps/invalid/scenarios",
                        "GET",
                        null,
                        objectMapper
                ),
                HttpExecutionEngine.ExpectedDefinition.exact(400)
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.SUCCESS);
        assertThat(log.getResponseCode()).isEqualTo(400);
        assertThat(log.getPath()).isEqualTo("/apps/invalid/scenarios");
        assertThat(log.getMethod()).isEqualTo("GET");
    }

    @Test
    void tearDownModeDeletesCreatedResourceAfterSuccessfulPost() throws Exception {
        AtomicBoolean deleted = new AtomicBoolean(false);
        server.createContext("/orders", exchange -> writeResponse(exchange, 201, "{\"id\":9}"));
        server.createContext("/orders/9", exchange -> {
            deleted.set(true);
            writeResponse(exchange, 204, "");
        });
        server.start();

        ApiEndpoint createOrder = api(ApiMethod.POST, "/orders");
        ApiEndpoint deleteOrder = api(ApiMethod.DELETE, "/orders/{orderId}");
        when(apiEndpointService.findCleanupEndpoint(createOrder)).thenReturn(Optional.of(deleteOrder));

        ExecutionStepLog log = engine.execute(
                execution(environment("{}"), true),
                null,
                null,
                createOrder,
                "create order",
                new HttpExecutionEngine.RequestDefinition(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        null,
                        objectMapper.readTree("{\"item\":\"book\"}"),
                        objectMapper
                ),
                new HttpExecutionEngine.ExpectedDefinition(201, objectMapper.readTree("{\"id\":9}"))
        );

        assertThat(log.getStatus()).isEqualTo(ExecutionStepStatus.SUCCESS);
        assertThat(deleted).isTrue();
        ArgumentCaptor<ExecutionStepLog> captor = ArgumentCaptor.forClass(ExecutionStepLog.class);
        org.mockito.Mockito.verify(stepLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ExecutionStepLog::getStepName)
                .containsExactly("create order", "tearDown: create order");
        assertThat(captor.getAllValues()).extracting(ExecutionStepLog::getPath)
                .containsExactly("/orders", "/orders/9");
    }

    private Environment environment(String headers) {
        return environment(headers, "http://localhost:" + server.getAddress().getPort());
    }

    private Environment environment(String headers, String baseUrl) {
        return Environment.builder()
                .app(App.builder().name("FlowOps").build())
                .name("QA")
                .baseUrl(baseUrl)
                .authType(AuthType.NONE)
                .headers(headers)
                .defaultTestLevel(TestLevel.SMOKE)
                .defaultTestLevelSource(TestLevelSource.MANUAL)
                .build();
    }

    private Execution execution(Environment environment) {
        return execution(environment, false);
    }

    private Execution execution(Environment environment, boolean tearDownMode) {
        return Execution.builder()
                .app(environment.getApp())
                .environment(environment)
                .executionType(ExecutionType.API)
                .targetId(1L)
                .triggerSource(ExecutionTriggerSource.MANUAL)
                .executionMode(ExecutionMode.RUN_EXISTING)
                .testLevel(TestLevel.SMOKE)
                .name("test")
                .status(ExecutionStatus.RUNNING)
                .totalCount(0)
                .passedCount(0)
                .failedCount(0)
                .tearDownMode(tearDownMode)
                .createdBy("tester")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ApiEndpoint api(ApiMethod method, String path) {
        return ApiEndpoint.builder()
                .app(App.builder().name("FlowOps").build())
                .method(method)
                .path(path)
                .deprecated(false)
                .build();
    }

    private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
