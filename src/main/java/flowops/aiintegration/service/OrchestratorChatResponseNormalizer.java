package flowops.aiintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final ApiEndpointService apiEndpointService;
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
        Map<String, ApiEndpoint> endpoints = endpointsByResponseKey(app, request.context());
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
            Map<String, ApiEndpoint> endpoints
    ) {
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
        Map<Long, ApiEndpoint> selectedEndpoints = new LinkedHashMap<>();
        for (JsonNode draft : data.path("drafts")) {
            ApiEndpoint endpoint = resolveDraftEndpoint(app.getId(), draft, endpoints);
            selectedEndpoints.putIfAbsent(endpoint.getId(), endpoint);
            savedDrafts.add(draftRepository.save(GeneratedTestCaseDraft.builder()
                    .generation(generation)
                    .apiEndpoint(endpoint)
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
                        .apiEndpoint(endpoint)
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

    private Map<String, ApiEndpoint> endpointsByResponseKey(App app, JsonNode context) {
        Map<String, ApiEndpoint> endpoints = new LinkedHashMap<>();
        JsonNode inventory = context == null ? null : context.get("api_inventory");
        JsonNode endpointNodes = inventory == null ? null : inventory.get("endpoints");
        if (endpointNodes == null || !endpointNodes.isArray()) {
            return endpoints;
        }
        for (JsonNode endpointNode : endpointNodes) {
            ApiEndpoint endpoint = resolveContextEndpoint(app.getId(), endpointNode);
            registerEndpoint(endpoints, endpointNode, endpoint);
        }
        return endpoints;
    }

    private ApiEndpoint resolveContextEndpoint(Long appId, JsonNode endpointNode) {
        Long endpointId = parseLongOrNull(firstText(endpointNode, "endpoint_id", text(endpointNode, "apiId")));
        if (endpointId != null) {
            ApiEndpoint endpoint = apiEndpointService.getApiEndpoint(endpointId);
            if (!Objects.equals(endpoint.getApp().getId(), appId)) {
                throw new IllegalArgumentException("API endpoint does not belong to the requested app.");
            }
            return endpoint;
        }
        EndpointTarget target = endpointTarget(endpointNode);
        return apiEndpointService.findFirstByAppIdAndMethodAndPath(appId, target.method(), target.path());
    }

    private ApiEndpoint resolveDraftEndpoint(Long appId, JsonNode draft, Map<String, ApiEndpoint> endpoints) {
        ApiEndpoint endpoint = firstEndpoint(
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
        ApiEndpoint byId = resolveByEndpointId(appId, draft);
        if (byId != null) {
            return byId;
        }
        EndpointTarget target = endpointTarget(draft);
        if (target == null) {
            target = endpointTarget(draft.get("selectedEndpoint"));
        }
        if (target == null) {
            throw new IllegalArgumentException("Testcase draft does not include a resolvable endpoint.");
        }
        return apiEndpointService.findFirstByAppIdAndMethodAndPath(appId, target.method(), target.path());
    }

    private ApiEndpoint resolveByEndpointId(Long appId, JsonNode draft) {
        Long endpointId = firstLong(
                text(draft, "apiId"),
                text(draft, "endpoint_id"),
                nestedText(draft, "selectedEndpoint", "id")
        );
        if (endpointId == null) {
            return null;
        }
        try {
            ApiEndpoint endpoint = apiEndpointService.getApiEndpoint(endpointId);
            return Objects.equals(endpoint.getApp().getId(), appId) ? endpoint : null;
        } catch (Exception exception) {
            log.debug("Failed to resolve orchestrator draft endpoint by id {}: {}", endpointId, exception.getMessage());
            return null;
        }
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

    private void registerEndpoint(Map<String, ApiEndpoint> endpoints, JsonNode source, ApiEndpoint endpoint) {
        putEndpoint(endpoints, text(source, "endpoint_id"), endpoint);
        putEndpoint(endpoints, text(source, "apiId"), endpoint);
        putEndpoint(endpoints, String.valueOf(endpoint.getId()), endpoint);
        putEndpoint(endpoints, endpoint.getMethod().name() + ":" + endpoint.getPath(), endpoint);
    }

    private ApiEndpoint firstEndpoint(Map<String, ApiEndpoint> endpoints, String... keys) {
        for (String key : keys) {
            if (key != null && endpoints.containsKey(key)) {
                return endpoints.get(key);
            }
        }
        return null;
    }

    private void putEndpoint(Map<String, ApiEndpoint> endpoints, String key, ApiEndpoint endpoint) {
        if (key != null && !key.isBlank()) {
            endpoints.put(key, endpoint);
        }
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
}
