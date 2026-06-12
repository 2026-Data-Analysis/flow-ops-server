package flowops.aiintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.integration.ai.AiAgentContracts.OrchestratorAgentResultPayload;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatDataPayload;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatRequest;
import flowops.integration.ai.AiAgentContracts.OrchestratorChatResponse;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationApiSelection;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import flowops.testgeneration.dto.response.GeneratedTestCaseDraftResponse;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrchestratorChatResponseNormalizer {

    private final AppService appService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiEndpointService apiEndpointService;
    private final ApiInventoryRepository apiInventoryRepository;
    private final TestGenerationRepository testGenerationRepository;
    private final TestGenerationApiSelectionRepository selectionRepository;
    private final GeneratedTestCaseDraftRepository draftRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrchestratorChatResponse normalize(OrchestratorChatRequest request, OrchestratorChatResponse response) {
        if (response == null || response.data() == null || response.data().agent_results() == null) {
            return response;
        }
        Long appId = parseLongOrNull(request.project_id());
        if (appId == null) {
            return response;
        }

        App app = appService.getApp(appId);
        Map<String, ResolvedEndpoint> endpoints = endpointsByResponseKey(app, request.context());
        List<OrchestratorAgentResultPayload> normalizedResults = new ArrayList<>();
        boolean changed = false;

        for (OrchestratorAgentResultPayload result : response.data().agent_results()) {
            if (!isTestCaseResult(result) || !hasDrafts(result.data())) {
                normalizedResults.add(result);
                continue;
            }
            try {
                JsonNode normalizedData = normalizeTestCaseData(app, request, result.data(), endpoints);
                normalizedResults.add(new OrchestratorAgentResultPayload(
                        result.agent_type(),
                        result.success(),
                        normalizedData,
                        result.error_message()
                ));
                changed = true;
            } catch (Exception exception) {
                log.warn("Failed to normalize orchestrator chat testcase result. appId={}, agentType={}, error={}",
                        appId,
                        result.agent_type(),
                        exception.getMessage());
                normalizedResults.add(result);
            }
        }

        if (!changed) {
            return response;
        }
        OrchestratorChatDataPayload normalizedData = new OrchestratorChatDataPayload(
                response.data().dispatched_agents(),
                normalizedResults,
                response.data().summary()
        );
        return new OrchestratorChatResponse(
                response.success(),
                normalizedData,
                response.error_code(),
                response.error_message(),
                response.trace_id()
        );
    }

    private JsonNode normalizeTestCaseData(
            App app,
            OrchestratorChatRequest request,
            JsonNode data,
            Map<String, ResolvedEndpoint> endpoints
    ) {
        List<ResolvedDraft> resolvedDrafts = new ArrayList<>();
        Map<String, ResolvedEndpoint> selectedEndpoints = new LinkedHashMap<>();
        for (JsonNode draft : data.path("drafts")) {
            ResolvedEndpoint resolvedEndpoint = resolveDraftEndpoint(app, draft, endpoints);
            selectedEndpoints.putIfAbsent(selectionKey(resolvedEndpoint), resolvedEndpoint);
            resolvedDrafts.add(new ResolvedDraft(draft, resolvedEndpoint));
        }

        TestGeneration generation = testGenerationRepository.save(TestGeneration.builder()
                .app(app)
                .environment(null)
                .status(TestGenerationStatus.PROCESSING)
                .requestedBy("orchestrator")
                .contextSummary(request.user_prompt())
                .existingCount(0)
                .newCount(0)
                .duplicateCount(0)
                .createdAt(LocalDateTime.now())
                .build());

        List<GeneratedTestCaseDraft> savedDrafts = new ArrayList<>();
        for (ResolvedDraft resolvedDraft : resolvedDrafts) {
            JsonNode draft = resolvedDraft.draft();
            ApiEndpoint endpoint = resolvedDraft.endpoint().apiEndpoint();
            ApiInventory inventory = resolvedDraft.endpoint().apiInventory();
            // 에이전트가 내려준 draft별 type/risk_level 원본 로그 (테스트 레벨 매핑 검증용)
            log.info("[Orchestrator testcase draft] title='{}' type='{}' risk_level(raw from agent)='{}'",
                    text(draft, "title"), text(draft, "type"),
                    firstText(draft, "riskLevel", text(draft, "risk_level")));
            savedDrafts.add(draftRepository.save(GeneratedTestCaseDraft.builder()
                    .generation(generation)
                    .apiEndpoint(endpoint)
                    .apiInventory(inventory)
                    .title(defaultIfBlank(text(draft, "title"), endpoint.getMethod() + " " + endpoint.getPath()))
                    .description(text(draft, "description"))
                    .type(defaultIfBlank(text(draft, "type"), "HAPPY_PATH"))
                    .riskLevel(firstText(draft, "riskLevel", text(draft, "risk_level")))
                    .userRole(text(draft, "userRole"))
                    .stateCondition(text(draft, "stateCondition"))
                    .dataVariant(text(draft, "dataVariant"))
                    .requestSpec(jsonToStorageText(mergeExecutionTarget(
                            firstNode(draft, "requestSpec", "request"),
                            firstText(draft, "executionEndpoint", text(draft, "execution_endpoint")),
                            firstText(draft, "executionMethod", text(draft, "execution_method")),
                            endpoint
                    )))
                    .expectedSpec(jsonToStorageText(firstNode(draft, "expectedSpec", "expected")))
                    .assertionSpec(jsonToStorageText(firstNode(draft, "assertionSpec", "assertion")))
                    .duplicate(draft.path("duplicate").asBoolean(false))
                    .selectedForSave(false)
                    .createdAt(LocalDateTime.now())
                    .build()));
        }

        selectedEndpoints.values().stream()
                .map(endpoint -> TestGenerationApiSelection.builder()
                        .generation(generation)
                        .apiEndpoint(endpoint.apiEndpoint())
                        .apiInventory(endpoint.apiInventory())
                        .build())
                .forEach(selectionRepository::save);

        int duplicateCount = (int) savedDrafts.stream().filter(GeneratedTestCaseDraft::isDuplicate).count();
        int newCount = savedDrafts.size() - duplicateCount;
        generation.markCompleted(newCount, newCount, duplicateCount, null);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("generationId", generation.getId());
        normalized.set("drafts", objectMapper.valueToTree(
                savedDrafts.stream().map(GeneratedTestCaseDraftResponse::from).toList()
        ));
        return normalized;
    }

    private Map<String, ResolvedEndpoint> endpointsByResponseKey(App app, JsonNode context) {
        Map<String, ResolvedEndpoint> endpoints = new LinkedHashMap<>();
        List<ApiInventory> inventories = apiInventoryRepository.findByRepositoryInfoAppIdOrderByIdDesc(app.getId());
        for (ApiInventory inventory : inventories) {
            ResolvedEndpoint endpoint = resolvedInventory(app, inventory);
            registerEndpoint(endpoints, inventory, endpoint);
        }
        JsonNode inventory = context == null ? null : context.get("api_inventory");
        JsonNode endpointNodes = inventory == null ? null : inventory.get("endpoints");
        if (endpointNodes == null || !endpointNodes.isArray()) {
            return endpoints;
        }
        for (JsonNode endpointNode : endpointNodes) {
            ResolvedEndpoint endpoint = resolveContextEndpoint(app, endpointNode);
            registerEndpoint(endpoints, endpointNode, endpoint);
        }
        return endpoints;
    }

    private ResolvedEndpoint resolveContextEndpoint(App app, JsonNode endpointNode) {
        Long id = parseLongOrNull(firstText(endpointNode, "endpoint_id", text(endpointNode, "apiId")));
        if (id != null) {
            java.util.Optional<ApiInventory> inventory = findInventoryByIdAndAppId(id, app.getId());
            if (inventory.isPresent()) {
                return resolvedInventory(app, inventory.get());
            }
            java.util.Optional<ApiEndpoint> endpoint = findEndpointByIdAndAppId(id, app.getId());
            if (endpoint.isPresent()) {
                return new ResolvedEndpoint(endpoint.get(), null);
            }
        }
        EndpointTarget target = endpointTarget(endpointNode);
        if (target == null) {
            throw new IllegalArgumentException("API endpoint does not belong to the requested app.");
        }
        return findEndpointByMethodAndPath(app.getId(), target.method(), target.path())
                .map(endpoint -> new ResolvedEndpoint(endpoint, null))
                .orElseThrow(() -> new IllegalArgumentException("API endpoint does not belong to the requested app."));
    }

    private ResolvedEndpoint resolveDraftEndpoint(App app, JsonNode draft, Map<String, ResolvedEndpoint> endpoints) {
        ResolvedEndpoint endpoint = firstEndpoint(
                endpoints,
                text(draft, "apiId"),
                text(draft, "endpoint_id"),
                nestedText(draft, "selectedEndpoint", "id")
        );
        if (endpoint != null) {
            return endpoint;
        }
        // 채팅 흐름은 context에 api_inventory가 없을 수 있어 endpoints 맵이 비어 있다.
        // 이 경우 에이전트가 내려준 숫자 apiId/endpoint_id를 직접 조회해 method/path를 채운다.
        // (이게 없으면 프론트가 엔드포인트명 대신 "API #<id>"로 표시된다.)
        ResolvedEndpoint byId = resolveById(app, draft);
        if (byId != null) {
            return byId;
        }
        EndpointTarget target = endpointTarget(draft);
        if (target == null) {
            target = endpointTarget(draft.get("selectedEndpoint"));
        }
        if (target == null) {
            throw unresolvedDraftException(draft, endpoints);
        }
        return findEndpointByMethodAndPath(app.getId(), target.method(), target.path())
                .map(endpointByPath -> new ResolvedEndpoint(endpointByPath, null))
                .orElseThrow(() -> unresolvedDraftException(draft, endpoints));
    }

    private ResolvedEndpoint resolveById(App app, JsonNode draft) {
        Long id = firstLong(
                text(draft, "apiId"),
                text(draft, "endpoint_id"),
                nestedText(draft, "selectedEndpoint", "id")
        );
        if (id == null) {
            return null;
        }
        return findInventoryByIdAndAppId(id, app.getId())
                .map(inventory -> resolvedInventory(app, inventory))
                .or(() -> findEndpointByIdAndAppId(id, app.getId()).map(endpoint -> new ResolvedEndpoint(endpoint, null)))
                .orElse(null);
    }

    private java.util.Optional<ApiInventory> findInventoryByIdAndAppId(Long inventoryId, Long appId) {
        java.util.Optional<ApiInventory> resolved = apiInventoryRepository.findById(inventoryId);
        return (resolved == null ? java.util.Optional.<ApiInventory>empty() : resolved)
                .filter(candidate -> candidate.getRepositoryInfo() == null
                        || candidate.getRepositoryInfo().getApp() == null
                        || Objects.equals(candidate.getRepositoryInfo().getApp().getId(), appId));
    }

    private ResolvedEndpoint resolvedInventory(App app, ApiInventory inventory) {
        return new ResolvedEndpoint(apiEndpointService.findOrCreateFromInventory(app, inventory), inventory);
    }

    private java.util.Optional<ApiEndpoint> findEndpointByIdAndAppId(Long endpointId, Long appId) {
        return apiEndpointRepository.findById(endpointId)
                .filter(endpoint -> endpoint.getApp() != null)
                .filter(endpoint -> Objects.equals(endpoint.getApp().getId(), appId));
    }

    private java.util.Optional<ApiEndpoint> findEndpointByMethodAndPath(Long appId, ApiMethod method, String path) {
        return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(appId, method, path);
    }

    private Long firstLong(String... values) {
        for (String value : values) {
            Long parsed = parseLongOrNull(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private void registerEndpoint(Map<String, ResolvedEndpoint> endpoints, JsonNode source, ResolvedEndpoint endpoint) {
        putEndpoint(endpoints, text(source, "endpoint_id"), endpoint);
        putEndpoint(endpoints, text(source, "apiId"), endpoint);
        if (endpoint.apiInventory() != null) {
            putEndpoint(endpoints, String.valueOf(endpoint.apiInventory().getId()), endpoint);
        }
        putEndpoint(endpoints, String.valueOf(endpoint.apiEndpoint().getId()), endpoint);
        putEndpoint(endpoints, endpoint.apiEndpoint().getMethod().name() + ":" + endpoint.apiEndpoint().getPath(), endpoint);
    }

    private void registerEndpoint(Map<String, ResolvedEndpoint> endpoints, ApiInventory inventory, ResolvedEndpoint endpoint) {
        putEndpoint(endpoints, String.valueOf(inventory.getId()), endpoint);
        putEndpoint(endpoints, inventory.getMethod().name() + ":" + inventory.getEndpointPath(), endpoint);
        if (endpoint.apiEndpoint() != null) {
            putEndpoint(endpoints, String.valueOf(endpoint.apiEndpoint().getId()), endpoint);
        }
    }

    private ResolvedEndpoint firstEndpoint(Map<String, ResolvedEndpoint> endpoints, String... keys) {
        for (String key : keys) {
            if (key != null && endpoints.containsKey(key)) {
                return endpoints.get(key);
            }
        }
        return null;
    }

    private void putEndpoint(Map<String, ResolvedEndpoint> endpoints, String key, ResolvedEndpoint endpoint) {
        if (key != null && !key.isBlank()) {
            endpoints.put(key, endpoint);
        }
    }

    private IllegalArgumentException unresolvedDraftException(JsonNode draft, Map<String, ResolvedEndpoint> endpoints) {
        String draftApiId = firstText(draft, "apiId", text(draft, "endpoint_id"));
        List<Long> availableEndpointIds = endpoints.values().stream()
                .map(endpoint -> endpoint.apiEndpoint().getId())
                .distinct()
                .toList();
        List<Long> availableInventoryIds = endpoints.values().stream()
                .map(ResolvedEndpoint::apiInventory)
                .filter(Objects::nonNull)
                .map(ApiInventory::getId)
                .distinct()
                .toList();
        List<String> availableMethodPaths = endpoints.values().stream()
                .map(endpoint -> endpoint.apiEndpoint().getMethod().name() + ":" + endpoint.apiEndpoint().getPath())
                .distinct()
                .toList();
        log.warn("Failed to resolve testcase draft endpoint. draftApiId={}, availableEndpointIds={}, availableInventoryIds={}, availableMethodPaths={}",
                draftApiId,
                availableEndpointIds,
                availableInventoryIds,
                availableMethodPaths);
        return new IllegalArgumentException("Testcase draft does not include a resolvable endpoint.");
    }

    private String selectionKey(ResolvedEndpoint endpoint) {
        return endpoint.apiInventory() == null
                ? "endpoint:" + endpoint.apiEndpoint().getId()
                : "inventory:" + endpoint.apiInventory().getId();
    }

    private EndpointTarget endpointTarget(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String method = firstText(node, "method", firstText(node, "executionMethod", text(node, "execution_method")));
        String path = firstText(node, "path", firstText(node, "endpoint", firstText(node, "executionEndpoint", text(node, "execution_endpoint"))));
        String endpointKey = firstText(node, "endpoint_id", text(node, "apiId"));
        if ((method == null || path == null || path.isBlank()) && endpointKey != null) {
            int separator = endpointKey.indexOf(':');
            if (separator > 0) {
                method = endpointKey.substring(0, separator);
                path = endpointKey.substring(separator + 1);
            }
        }
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        return new EndpointTarget(ApiMethod.valueOf(method.trim().toUpperCase()), path.trim());
    }

    private JsonNode mergeExecutionTarget(JsonNode requestSpec, String executionEndpoint, String executionMethod, ApiEndpoint endpoint) {
        ObjectNode target = requestSpec != null && requestSpec.isObject()
                ? requestSpec.deepCopy()
                : objectMapper.createObjectNode();
        if (!target.has("endpoint") && !target.has("path")) {
            target.put("endpoint", defaultIfBlank(executionEndpoint, endpoint.getPath()));
        }
        if (!target.has("method") && !target.has("httpMethod") && !target.has("http_method")) {
            target.put("method", defaultIfBlank(executionMethod, endpoint.getMethod().name()).toUpperCase());
        }
        if (requestSpec != null
                && !requestSpec.isNull()
                && !requestSpec.isMissingNode()
                && !requestSpec.isObject()
                && !target.has("body")) {
            target.set("body", requestSpec);
        }
        return target;
    }

    private JsonNode firstNode(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.isMissingNode()) {
                return value;
            }
        }
        return null;
    }

    private String nestedText(JsonNode node, String objectField, String textField) {
        JsonNode object = node == null ? null : node.get(objectField);
        return text(object, textField);
    }

    private String firstText(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText();
    }

    private String jsonToStorageText(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return value.toString();
        }
    }

    private boolean isTestCaseResult(OrchestratorAgentResultPayload result) {
        if (result == null || !result.success() || result.agent_type() == null) {
            return false;
        }
        String agentType = result.agent_type().toLowerCase();
        return agentType.contains("testcase") || agentType.contains("test_case");
    }

    private boolean hasDrafts(JsonNode data) {
        return data != null && data.has("drafts") && data.get("drafts").isArray();
    }

    private Long parseLongOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record EndpointTarget(ApiMethod method, String path) {
    }

    private record ResolvedEndpoint(ApiEndpoint apiEndpoint, ApiInventory apiInventory) {
    }

    private record ResolvedDraft(JsonNode draft, ResolvedEndpoint endpoint) {
    }
}
