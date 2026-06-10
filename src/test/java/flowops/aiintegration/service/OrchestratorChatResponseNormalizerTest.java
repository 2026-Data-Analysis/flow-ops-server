package flowops.aiintegration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.integration.ai.AiAgentContracts.OrchestratorAgentResultPayload;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatDataPayload;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatRequest;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatResponse;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrchestratorChatResponseNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

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
    void normalizePersistsTestcaseDraftsAndReturnsSaveableDraftData() throws Exception {
        App app = app(1L);
        ApiEndpoint endpoint = endpoint(10L, app);
        JsonNode context = objectMapper.readTree("""
                {
                  "api_inventory": {
                    "endpoints": [
                      {"endpoint_id": "10", "method": "POST", "path": "/orders"}
                    ]
                  }
                }
                """);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "generationId": "agent-gen",
                  "drafts": [
                    {
                      "apiId": "10",
                      "title": "Order creation succeeds",
                      "type": "HAPPY_PATH",
                      "requestSpec": {"body": {"productId": 1}},
                      "expectedSpec": {"status": 201},
                      "assertionSpec": {"assertions": ["status == 201"]}
                    }
                  ]
                }
                """);
        OrchestratorChatRequest request = new OrchestratorChatRequest("1", "주문 생성 테스트 만들어줘", context);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-1"
        );

        when(appService.getApp(1L)).thenReturn(app);
        when(apiEndpointService.getApiEndpoint(10L)).thenReturn(endpoint);
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 77L);
            return generation;
        });
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 1001L);
            return draft;
        });

        OrchestratorChatResponse normalized = service().normalize(request, response);

        JsonNode normalizedData = normalized.data().agent_results().get(0).data();
        assertThat(normalizedData.path("generationId").asLong()).isEqualTo(77L);
        JsonNode draft = normalizedData.path("drafts").get(0);
        assertThat(draft.path("id").asLong()).isEqualTo(1001L);
        assertThat(draft.path("generationId").asLong()).isEqualTo(77L);
        assertThat(draft.path("selectedEndpoint").path("id").asLong()).isEqualTo(10L);
        assertThat(draft.path("selectedEndpoint").path("method").asText()).isEqualTo("POST");
        assertThat(draft.path("selectedEndpoint").path("path").asText()).isEqualTo("/orders");
        assertThat(draft.path("requestSpec").asText()).contains("\"endpoint\":\"/orders\"");
    }

    @Test
    void normalizeResolvesEndpointByNumericApiIdWhenContextHasNoInventory() throws Exception {
        App app = app(1L);
        ApiEndpoint endpoint = endpoint(1999L, app);
        // 채팅 흐름은 context에 api_inventory를 포함하지 않는다.
        JsonNode context = objectMapper.readTree("""
                {"api_server_url": "http://localhost:8080", "env_name": "local"}
                """);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "drafts": [
                    {
                      "apiId": "1999",
                      "title": "Order creation succeeds",
                      "type": "success"
                    }
                  ]
                }
                """);
        OrchestratorChatRequest request = new OrchestratorChatRequest("1", "주문 생성 테스트 만들어줘", context);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-2"
        );

        when(appService.getApp(1L)).thenReturn(app);
        when(apiEndpointService.getApiEndpoint(1999L)).thenReturn(endpoint);
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 88L);
            return generation;
        });
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 2001L);
            return draft;
        });

        OrchestratorChatResponse normalized = service().normalize(request, response);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("selectedEndpoint").path("id").asLong()).isEqualTo(1999L);
        assertThat(draft.path("selectedEndpoint").path("method").asText()).isEqualTo("POST");
        assertThat(draft.path("selectedEndpoint").path("path").asText()).isEqualTo("/orders");
        assertThat(draft.path("endpointName").asText()).isEqualTo("POST /orders");
    }

    @Test
    void normalizeReturnsOriginalResponseWithoutSavingWhenDraftEndpointCannotBeResolved() throws Exception {
        App app = app(1L);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "drafts": [
                    {
                      "apiId": "api-order-001",
                      "title": "Order creation succeeds",
                      "requestSpec": {"method": "POST", "body": {"productId": 1}}
                    }
                  ]
                }
                """);
        OrchestratorChatRequest request = new OrchestratorChatRequest("1", "주문 생성 테스트 만들어줘", null);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-3"
        );

        when(appService.getApp(1L)).thenReturn(app);

        OrchestratorChatResponse normalized = service().normalize(request, response);

        assertThat(normalized).isSameAs(response);
        verify(testGenerationRepository, never()).save(any(TestGeneration.class));
        verify(draftRepository, never()).save(any(GeneratedTestCaseDraft.class));
    }

    private OrchestratorChatResponseNormalizer service() {
        return new OrchestratorChatResponseNormalizer(
                appService,
                apiEndpointService,
                testGenerationRepository,
                selectionRepository,
                draftRepository,
                objectMapper
        );
    }

    private App app(Long id) {
        App app = App.builder()
                .name("app-" + id)
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
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
}
