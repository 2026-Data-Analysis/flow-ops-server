package flowops.aiintegration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.domain.entity.ApiInventoryStatus;
import flowops.apiinventory.repository.ApiInventoryRepository;
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
import java.util.Optional;
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
    private ApiEndpointRepository apiEndpointRepository;

    @Mock
    private ApiEndpointService apiEndpointService;

    @Mock
    private ApiInventoryRepository apiInventoryRepository;

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
        when(apiEndpointRepository.findById(10L)).thenReturn(Optional.of(endpoint));
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
        when(apiEndpointRepository.findById(1999L)).thenReturn(Optional.of(endpoint));
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
    void normalizeSkipsContextEndpointThatDoesNotBelongToApp() throws Exception {
        App app = app(1L);
        App otherApp = app(2L);
        ApiEndpoint endpoint = endpoint(1999L, app);
        ApiEndpoint otherEndpoint = endpoint(9999L, otherApp, "/other");
        JsonNode context = objectMapper.readTree("""
                {
                  "api_inventory": {
                    "endpoints": [
                      {"endpoint_id": "9999"}
                    ]
                  }
                }
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
        OrchestratorChatRequest request = new OrchestratorChatRequest("1", "create order test", context);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-context-skip"
        );

        when(appService.getApp(1L)).thenReturn(app);
        when(apiEndpointRepository.findById(9999L)).thenReturn(Optional.of(otherEndpoint));
        when(apiEndpointRepository.findById(1999L)).thenReturn(Optional.of(endpoint));
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 91L);
            return generation;
        });
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 2004L);
            return draft;
        });

        OrchestratorChatResponse normalized = service().normalize(request, response);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("selectedEndpoint").path("id").asLong()).isEqualTo(1999L);
        assertThat(draft.path("selectedEndpoint").path("path").asText()).isEqualTo("/orders");
    }

    @Test
    void normalizeResolvesContextInventoryIdByMethodAndPath() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/mates/chat");
        JsonNode context = objectMapper.readTree("""
                {
                  "api_inventory": {
                    "project_id": "3",
                    "endpoints": [
                      {"endpoint_id": "2241", "method": "POST", "path": "/mates/chat", "summary": "메이트 채팅"}
                    ]
                  },
                  "inventory_lookup": {
                    "projectId": 1,
                    "appId": 3,
                    "repositoryId": 10,
                    "branchName": "main",
                    "keyword": "메이트 채팅"
                  }
                }
                """);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "drafts": [
                    {
                      "apiId": "POST:/mates/chat",
                      "title": "Mate chat succeeds",
                      "type": "HAPPY_PATH"
                    }
                  ]
                }
                """);
        OrchestratorChatRequest request = new OrchestratorChatRequest("3", "메이트 채팅", context);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-5"
        );

        when(appService.getApp(3L)).thenReturn(app);
        when(apiEndpointRepository.findById(2241L)).thenReturn(Optional.empty());
        when(apiEndpointRepository.findFirstByAppIdAndMethodAndPath(3L, ApiMethod.POST, "/mates/chat"))
                .thenReturn(Optional.of(endpoint));
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 89L);
            return generation;
        });
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 2002L);
            return draft;
        });

        OrchestratorChatResponse normalized = service().normalize(request, response);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiId").asLong()).isEqualTo(2056L);
        assertThat(draft.path("selectedEndpoint").path("id").asLong()).isEqualTo(2056L);
        assertThat(draft.path("selectedEndpoint").path("path").asText()).isEqualTo("/mates/chat");
    }

    @Test
    void normalizeResolvesNumericApiIdAsInventoryIdAndReturnsInventoryMetadata() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/mates/chat");
        ApiInventory inventory = inventory(2248L, "/mates/chat");
        JsonNode context = objectMapper.readTree("""
                {
                  "api_inventory": {
                    "project_id": "1",
                    "endpoints": [
                      {"endpoint_id": "2248", "method": "POST", "path": "/mates/chat"}
                    ]
                  }
                }
                """);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "drafts": [
                    {
                      "apiId": "2248",
                      "title": "Mate chat succeeds",
                      "type": "HAPPY_PATH"
                    }
                  ]
                }
                """);
        OrchestratorChatRequest request = new OrchestratorChatRequest("3", "chat tests", context);
        OrchestratorChatResponse response = new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, agentData, null)),
                        "done"
                ),
                null,
                null,
                "trace-6"
        );

        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findById(2248L)).thenReturn(Optional.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", 90L);
            return generation;
        });
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", 2003L);
            return draft;
        });

        OrchestratorChatResponse normalized = service().normalize(request, response);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiId").asLong()).isEqualTo(2248L);
        assertThat(draft.path("apiInventoryId").asLong()).isEqualTo(2248L);
        assertThat(draft.path("apiEndpointId").asLong()).isEqualTo(2056L);
        assertThat(draft.path("endpointId").asText()).isEqualTo("POST:/mates/chat");
        assertThat(draft.path("selectedEndpoint").path("id").asLong()).isEqualTo(2248L);
        assertThat(draft.path("selectedEndpoint").path("apiInventoryId").asLong()).isEqualTo(2248L);
        assertThat(draft.path("selectedEndpoint").path("apiEndpointId").asLong()).isEqualTo(2056L);
    }

    @Test
    void normalizeResolvesNullDraftApiIdByMethodAndPath() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/projects");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        stubDraftPersistence(91L, 3001L);

        OrchestratorChatResponse normalized = service().normalize(
                requestWithData(3L),
                responseWithData("""
                        {
                          "drafts": [
                            {"title": "Create project", "method": "POST", "path": "/projects"}
                          ]
                        }
                        """)
        );

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiInventoryId").asLong()).isEqualTo(2249L);
        assertThat(draft.path("endpointId").asText()).isEqualTo("POST:/projects");
        verify(apiEndpointService, never()).getApiEndpointDetail(any());
    }

    @Test
    void normalizeResolvesNullDraftApiIdByRequestSpecMethodAndPath() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/projects");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        stubDraftPersistence(92L, 3002L);

        OrchestratorChatResponse normalized = normalizeAgentData(3L, """
                {
                  "drafts": [
                    {
                      "title": "Create project",
                      "requestSpec": {"method": "POST", "path": "/projects", "body": {"name": "FlowOps"}}
                    }
                  ]
                }
                """);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiInventoryId").asLong()).isEqualTo(2249L);
        assertThat(draft.path("requestSpec").asText()).contains("\"endpoint\":\"/projects\"");
    }

    @Test
    void normalizeResolvesEndpointIdAndApiIdMethodPathKeys() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/projects");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        stubDraftPersistence(93L, 3003L);

        OrchestratorChatResponse normalized = normalizeAgentData(3L, """
                {
                  "drafts": [
                    {"endpoint_id": "POST:/projects", "title": "Legacy project"},
                    {"apiId": "POST:/projects", "title": "New project"}
                  ]
                }
                """);

        JsonNode drafts = normalized.data().agent_results().get(0).data().path("drafts");
        assertThat(drafts).hasSize(2);
        assertThat(drafts.get(0).path("apiInventoryId").asLong()).isEqualTo(2249L);
        assertThat(drafts.get(1).path("apiInventoryId").asLong()).isEqualTo(2249L);
        verify(selectionRepository, times(1)).save(any());
    }

    @Test
    void normalizeResolvesNumericApiIdAsInventoryBeforeApiEndpointId() throws Exception {
        App app = app(3L);
        ApiEndpoint inventoryEndpoint = endpoint(2056L, app, "/projects");
        ApiEndpoint sameNumberEndpoint = endpoint(2249L, app, "/wrong");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(inventoryEndpoint);
        stubDraftPersistence(94L, 3004L);

        OrchestratorChatResponse normalized = normalizeAgentData(3L, """
                {
                  "drafts": [
                    {"apiId": "2249", "title": "Numeric inventory"}
                  ]
                }
                """);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiInventoryId").asLong()).isEqualTo(2249L);
        assertThat(draft.path("apiEndpointId").asLong()).isEqualTo(2056L);
        verify(apiEndpointRepository, never()).findById(2249L);
        assertThat(sameNumberEndpoint.getPath()).isEqualTo("/wrong");
    }

    @Test
    void normalizeDoesNotTreatNumericEndpointIdsAsEndpointKeysWhenMethodPathCanResolve() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(72L, app, "/projects");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        stubDraftPersistence(95L, 3005L);

        OrchestratorChatResponse normalized = normalizeAgentData(3L, """
                {
                  "drafts": [
                    {"endpointId": "72", "method": "POST", "path": "/projects", "title": "Project by path"}
                  ]
                }
                """);

        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("apiInventoryId").asLong()).isEqualTo(2249L);
        verify(apiEndpointRepository, never()).findById(72L);
    }

    @Test
    void normalizeKeepsUnresolvedDraftsWithoutFailingResolvedResults() throws Exception {
        App app = app(3L);
        ApiEndpoint endpoint = endpoint(2056L, app, "/projects");
        ApiInventory inventory = inventory(2249L, "/projects");
        when(appService.getApp(3L)).thenReturn(app);
        when(apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(3L)).thenReturn(List.of(inventory));
        when(apiEndpointService.findOrCreateFromInventory(app, inventory)).thenReturn(endpoint);
        stubDraftPersistence(96L, 3006L);

        OrchestratorChatResponse normalized = normalizeAgentData(3L, """
                {
                  "drafts": [
                    {"apiInventoryId": 2249, "title": "Resolved project"},
                    {"apiId": "missing-action", "title": "Unresolved action", "method": "GET", "path": "/missing"}
                  ]
                }
                """);

        JsonNode data = normalized.data().agent_results().get(0).data();
        assertThat(data.path("generationId").asLong()).isEqualTo(96L);
        assertThat(data.path("drafts")).hasSize(2);
        assertThat(data.path("drafts").get(0).path("apiInventoryId").asLong()).isEqualTo(2249L);
        assertThat(data.path("drafts").get(1).path("unresolved").asBoolean()).isTrue();
        assertThat(data.path("drafts").get(1).path("method").asText()).isEqualTo("GET");
        assertThat(data.path("drafts").get(1).path("path").asText()).isEqualTo("/missing");
        verify(testGenerationRepository, times(1)).save(any(TestGeneration.class));
        verify(apiEndpointService, never()).getApiEndpointDetail(any());
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

        assertThat(normalized).isNotSameAs(response);
        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("unresolved").asBoolean()).isTrue();
        assertThat(draft.path("resolveError").asText()).contains("resolvable endpoint");
        verify(testGenerationRepository, never()).save(any(TestGeneration.class));
        verify(draftRepository, never()).save(any(GeneratedTestCaseDraft.class));
    }

    @Test
    void normalizeReturnsOriginalResponseWithoutSavingWhenNumericApiIdDoesNotExist() throws Exception {
        App app = app(1L);
        JsonNode agentData = objectMapper.readTree("""
                {
                  "drafts": [
                    {
                      "apiId": "9999",
                      "title": "Order creation succeeds"
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
                "trace-4"
        );

        when(appService.getApp(1L)).thenReturn(app);
        when(apiEndpointRepository.findById(9999L)).thenReturn(Optional.empty());

        OrchestratorChatResponse normalized = service().normalize(request, response);

        assertThat(normalized).isNotSameAs(response);
        JsonNode draft = normalized.data().agent_results().get(0).data().path("drafts").get(0);
        assertThat(draft.path("unresolved").asBoolean()).isTrue();
        assertThat(draft.path("apiId").asText()).isEqualTo("9999");
        verify(testGenerationRepository, never()).save(any(TestGeneration.class));
        verify(draftRepository, never()).save(any(GeneratedTestCaseDraft.class));
    }

    private OrchestratorChatResponse normalizeAgentData(Long appId, String agentDataJson) throws Exception {
        return service().normalize(requestWithData(appId), responseWithData(agentDataJson));
    }

    private OrchestratorChatRequest requestWithData(Long appId) throws Exception {
        return new OrchestratorChatRequest(String.valueOf(appId), "generate tests", objectMapper.readTree("{}"));
    }

    private OrchestratorChatResponse responseWithData(String agentDataJson) throws Exception {
        return new OrchestratorChatResponse(
                true,
                new OrchestratorChatDataPayload(
                        List.of("testcase"),
                        List.of(new OrchestratorAgentResultPayload("testcase", true, objectMapper.readTree(agentDataJson), null)),
                        "done"
                ),
                null,
                null,
                "trace-generated"
        );
    }

    private void stubDraftPersistence(Long generationId, Long firstDraftId) {
        when(testGenerationRepository.save(any(TestGeneration.class))).thenAnswer(invocation -> {
            TestGeneration generation = invocation.getArgument(0);
            ReflectionTestUtils.setField(generation, "id", generationId);
            return generation;
        });
        final long[] index = {0};
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> {
            GeneratedTestCaseDraft draft = invocation.getArgument(0);
            ReflectionTestUtils.setField(draft, "id", firstDraftId + index[0]++);
            return draft;
        });
    }

    private OrchestratorChatResponseNormalizer service() {
        return new OrchestratorChatResponseNormalizer(
                appService,
                apiEndpointRepository,
                apiEndpointService,
                apiInventoryRepository,
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
        return endpoint(id, app, "/orders");
    }

    private ApiEndpoint endpoint(Long id, App app, String path) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .app(app)
                .method(ApiMethod.POST)
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
                .operationId("mateChat")
                .domainTag("MATE")
                .sourceType(ApiInventorySource.OPENAPI)
                .status(ApiInventoryStatus.ACTIVE)
                .authRequired(false)
                .build();
        ReflectionTestUtils.setField(inventory, "id", id);
        return inventory;
    }
}
