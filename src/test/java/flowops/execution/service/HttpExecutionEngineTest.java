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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;

class HttpExecutionEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private HttpExecutionEngine engine;
    private TestValidationResultRepository validationResultRepository;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        ExecutionStepLogRepository stepLogRepository = mock(ExecutionStepLogRepository.class);
        validationResultRepository = mock(TestValidationResultRepository.class);
        when(stepLogRepository.save(any(ExecutionStepLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(validationResultRepository.save(any(TestValidationResult.class))).thenAnswer(invocation -> invocation.getArgument(0));
        engine = new HttpExecutionEngine(
                stepLogRepository,
                validationResultRepository,
                mock(ScenarioStepRepository.class),
                mock(TestCaseRepository.class),
                mock(ApiEndpointService.class),
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

    private Environment environment(String headers) {
        return Environment.builder()
                .app(App.builder().name("FlowOps").build())
                .name("QA")
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .authType(AuthType.NONE)
                .headers(headers)
                .defaultTestLevel(TestLevel.SMOKE)
                .defaultTestLevelSource(TestLevelSource.MANUAL)
                .build();
    }

    private Execution execution(Environment environment) {
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
