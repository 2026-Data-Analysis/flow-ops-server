package flowops.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.AuthType;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.integration.ai.AiAgentContracts.TestCaseDraftPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WebClientAiTestGenerationGatewayTest {

    @Mock
    private AiClient aiClient;

    @Mock
    private ApiEndpointService apiEndpointService;

    @Mock
    private ApiInventoryRepository apiInventoryRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Test
    void generateDraftsSendsContractApiSchemaFieldsInPayload() throws Exception {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        ApiEndpoint endpoint = endpoint(2059L, app);

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of());
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(response("2059"));

        gateway().generateDrafts(generation, List.of(2059L), null);

        ArgumentCaptor<TestCaseGeneratorRequest> captor = ArgumentCaptor.forClass(TestCaseGeneratorRequest.class);
        verify(aiClient).generateTestCaseDrafts(captor.capture());
        assertThat(captor.getValue().apis()).hasSize(1);
        assertThat(captor.getValue().apis().get(0).apiId()).isEqualTo("2059");
        assertThat(captor.getValue().apis().get(0).requestSchema().get("type").asText()).isEqualTo("object");
        assertThat(captor.getValue().apis().get(0).responseSchema().get("status").asInt()).isEqualTo(201);
        assertThat(new ObjectMapper().writeValueAsString(captor.getValue().apis().get(0)))
                .contains("\"request_body_schema\"")
                .contains("\"response_schema\"")
                .doesNotContain("requestSchema")
                .doesNotContain("responseSchema");
    }

    @Test
    void generateDraftsSendsSameDomainApiListForLookupContext() {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        ApiEndpoint selectedEndpoint = endpoint(2059L, app, ApiMethod.POST, "/orders", "orders");
        ApiEndpoint listEndpoint = endpoint(2060L, app, ApiMethod.GET, "/orders", "orders");
        ApiEndpoint detailEndpoint = endpoint(2061L, app, ApiMethod.GET, "/orders/{orderId}", "orders");

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(selectedEndpoint);
        when(apiEndpointService.getApiEndpointsByDomain(41L, "orders"))
                .thenReturn(List.of(listEndpoint, selectedEndpoint, detailEndpoint));
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of());
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(response("2059"));

        gateway().generateDrafts(generation, List.of(2059L), null);

        ArgumentCaptor<TestCaseGeneratorRequest> captor = ArgumentCaptor.forClass(TestCaseGeneratorRequest.class);
        verify(aiClient).generateTestCaseDrafts(captor.capture());
        assertThat(captor.getValue().apis()).extracting(TestCaseApiPayload::apiId)
                .containsExactly("2059");
        assertThat(captor.getValue().domainApis()).hasSize(3);
        assertThat(captor.getValue().domainApis()).extracting(TestCaseApiPayload::method, TestCaseApiPayload::path)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("GET", "/orders"),
                        org.assertj.core.groups.Tuple.tuple("POST", "/orders"),
                        org.assertj.core.groups.Tuple.tuple("GET", "/orders/{orderId}")
                );
    }

    @Test
    void generateDraftsAcceptsEmptyDraftsResponse() {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        ApiEndpoint endpoint = endpoint(2059L, app);

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of());
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(new TestCaseGeneratorResponse("req-1", "57", List.of()));

        List<AiGeneratedDraftCommand> commands = gateway().generateDrafts(generation, List.of(2059L), null);

        assertThat(commands).isEmpty();
        verify(aiClient).generateTestCaseDrafts(any(TestCaseGeneratorRequest.class));
    }

    @Test
    void generateDraftsNormalizesEmptyJsonPayloadFieldsToNull() {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        ApiEndpoint endpoint = endpoint(2059L, app, "{}", "[]");
        TestCase existingTestCase = testCase(app, endpoint);

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of(existingTestCase));
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(new TestCaseGeneratorResponse("req-1", "57", List.of()));

        gateway().generateDrafts(generation, List.of(2059L), null);

        ArgumentCaptor<TestCaseGeneratorRequest> captor = ArgumentCaptor.forClass(TestCaseGeneratorRequest.class);
        verify(aiClient).generateTestCaseDrafts(captor.capture());
        TestCaseGeneratorRequest request = captor.getValue();
        assertThat(request.apis().get(0).requestSchema().isNull()).isTrue();
        assertThat(request.apis().get(0).responseSchema().isNull()).isTrue();
        assertThat(request.existingTestCases().get(0).requestSpec().isNull()).isTrue();
        assertThat(request.existingTestCases().get(0).expectedSpec().isNull()).isTrue();
        assertThat(request.existingTestCases().get(0).assertionSpec().isNull()).isTrue();
    }

    @Test
    void generateDraftsSendsEnvironmentExecutionContextInPayload() {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        Environment environment = environment(app);
        ApiEndpoint endpoint = endpoint(2059L, app);

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of());
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(new TestCaseGeneratorResponse("req-1", "57", List.of()));

        gateway().generateDrafts(generation, List.of(2059L), environment);

        ArgumentCaptor<TestCaseGeneratorRequest> captor = ArgumentCaptor.forClass(TestCaseGeneratorRequest.class);
        verify(aiClient).generateTestCaseDrafts(captor.capture());
        assertThat(captor.getValue().environment().environmentId()).isEqualTo("901");
        assertThat(captor.getValue().environment().baseUrl()).isEqualTo("https://api.example.com");
        assertThat(captor.getValue().environment().defaultTestLevel()).isEqualTo("SMOKE");
        assertThat(captor.getValue().environment().authType()).isEqualTo("BEARER");
        assertThat(captor.getValue().environment().authConfig().path("token").asText()).isEqualTo("sample-token");
        assertThat(captor.getValue().environment().headers().path("Authorization").asText()).isEqualTo("Bearer sample-token");
        assertThat(captor.getValue().environment().headers().path("X-Tenant").asText()).isEqualTo("tenant-a");
    }

    @Test
    void generateDraftsStoresExecutionEndpointFromAiResponseInRequestSpec() throws Exception {
        App app = app(41L);
        TestGeneration generation = generation(57L, app);
        ApiEndpoint endpoint = endpoint(2059L, app);

        when(apiInventoryRepository.findById(2059L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(2059L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.of(2059L)))
                .thenReturn(List.of());
        when(aiClient.generateTestCaseDrafts(any(TestCaseGeneratorRequest.class)))
                .thenReturn(response("2059", "/apps//scenarios", "GET"));

        List<AiGeneratedDraftCommand> commands = gateway().generateDrafts(generation, List.of(2059L), null);

        JsonNode requestSpec = new ObjectMapper().readTree(commands.get(0).requestSpec());
        assertThat(commands.get(0).riskLevel()).isEqualTo("SMOKE");
        assertThat(requestSpec.path("endpoint").asText()).isEqualTo("/apps//scenarios");
        assertThat(requestSpec.path("method").asText()).isEqualTo("GET");
        assertThat(requestSpec.path("body").asText()).isEqualTo("sample");
    }

    private WebClientAiTestGenerationGateway gateway() {
        return new WebClientAiTestGenerationGateway(
                aiClient,
                apiEndpointService,
                apiInventoryRepository,
                testCaseRepository,
                new ObjectMapper()
        );
    }

    private App app(Long id) {
        App app = App.builder()
                .name("app-" + id)
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private TestGeneration generation(Long id, App app) {
        TestGeneration generation = TestGeneration.builder()
                .app(app)
                .status(TestGenerationStatus.PROCESSING)
                .requestedBy("tester")
                .contextSummary("Generate useful regression test cases.")
                .existingCount(0)
                .newCount(0)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(generation, "id", id);
        return generation;
    }

    private Environment environment(App app) {
        Environment environment = Environment.builder()
                .app(app)
                .name("staging")
                .baseUrl("https://api.example.com")
                .authType(AuthType.BEARER)
                .authConfig("{\"token\":\"sample-token\"}")
                .headers("{\"Authorization\":\"Bearer sample-token\",\"X-Tenant\":\"tenant-a\"}")
                .defaultTestLevel(TestLevel.SMOKE)
                .defaultTestLevelSource(TestLevelSource.MANUAL)
                .build();
        ReflectionTestUtils.setField(environment, "id", 901L);
        return environment;
    }

    private ApiEndpoint endpoint(Long id, App app) {
        return endpoint(id, app, ApiMethod.POST, "/orders", "orders", "{\"type\":\"object\"}", "{\"status\":201}");
    }

    private ApiEndpoint endpoint(Long id, App app, String requestSchema, String responseSchema) {
        return endpoint(id, app, ApiMethod.POST, "/orders", "orders", requestSchema, responseSchema);
    }

    private ApiEndpoint endpoint(Long id, App app, ApiMethod method, String path, String domainTag) {
        return endpoint(id, app, method, path, domainTag, "{\"type\":\"object\"}", "{\"status\":201}");
    }

    private ApiEndpoint endpoint(Long id, App app, ApiMethod method, String path, String domainTag, String requestSchema, String responseSchema) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(method)
                .path(path)
                .domainTag(domainTag)
                .requestSchema(requestSchema)
                .responseSchema(responseSchema)
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", id);
        return endpoint;
    }

    private TestCase testCase(App app, ApiEndpoint endpoint) {
        TestCase testCase = TestCase.builder()
                .app(app)
                .apiEndpoint(endpoint)
                .name("Existing empty spec case")
                .description("Existing test case with empty JSON specs.")
                .type(TestCaseType.HAPPY_PATH)
                .testLevel(TestLevel.REGRESSION)
                .source(TestCaseSource.AUTO)
                .requestSpec("{}")
                .expectedSpec("{}")
                .assertionSpec("[]")
                .active(true)
                .version(1)
                .build();
        ReflectionTestUtils.setField(testCase, "id", 3001L);
        return testCase;
    }

    private TestCaseGeneratorResponse response(String apiId) {
        return response(apiId, null, null);
    }

    private TestCaseGeneratorResponse response(String apiId, String executionEndpoint, String executionMethod) {
        return new TestCaseGeneratorResponse(
                "req-1",
                "57",
                List.of(new TestCaseDraftPayload(
                        apiId,
                        null,
                        "Order creation succeeds",
                        "Verifies a successful order creation.",
                        "HAPPY_PATH",
                        "SMOKE",
                        "CUSTOMER",
                        "Signed in",
                        "single product",
                        executionEndpoint,
                        executionMethod,
                        new ObjectMapper().createObjectNode().put("body", "sample"),
                        new ObjectMapper().createObjectNode().put("status", 201),
                        new ObjectMapper().createObjectNode().put("assertion", "status == 201"),
                        false
                ))
        );
    }
}
