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
import flowops.integration.ai.AiAgentContracts.TestCaseDraftPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
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

    private ApiEndpoint endpoint(Long id, App app) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(ApiMethod.POST)
                .path("/orders")
                .domainTag("orders")
                .requestSchema("{\"type\":\"object\"}")
                .responseSchema("{\"status\":201}")
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", id);
        return endpoint;
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
                        null,
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
