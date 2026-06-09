package flowops.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.environment.domain.entity.Environment;
import flowops.execution.domain.entity.Execution;
import flowops.execution.domain.entity.ExecutionStepLog;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseApiPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseDraftPayload;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorRequest;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import flowops.integration.ai.AiAgentContracts.TestGenerationContext;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testgeneration.domain.entity.TestGeneration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.ai", name = "mock-enabled", havingValue = "false")
@Slf4j
public class WebClientAiTestGenerationGateway implements AiTestGenerationGateway {

    private static final String AGENT = "TEST_CASE_GENERATOR";

    private final AiClient aiClient;
    private final ApiEndpointService apiEndpointService;
    private final ApiInventoryRepository apiInventoryRepository;
    private final TestCaseRepository testCaseRepository;
    private final ObjectMapper objectMapper;

    public WebClientAiTestGenerationGateway(
            AiClient aiClient,
            ApiEndpointService apiEndpointService,
            ApiInventoryRepository apiInventoryRepository,
            TestCaseRepository testCaseRepository,
            ObjectMapper objectMapper
    ) {
        this.aiClient = aiClient;
        this.apiEndpointService = apiEndpointService;
        this.apiInventoryRepository = apiInventoryRepository;
        this.testCaseRepository = testCaseRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds, Environment sourceEnvironment) {
        log.info("Calling AI test case generator. generationId={}, appId={}, sourceEnvironmentId={}, apiCount={}",
                generation.getId(),
                generation.getApp() == null ? null : generation.getApp().getId(),
                sourceEnvironment == null ? null : sourceEnvironment.getId(),
                apiIds == null ? 0 : apiIds.size());
        Map<String, Long> responseApiIdToSourceApiId = new LinkedHashMap<>();
        List<ApiSelection> selections = apiIds.stream()
                .map(apiId -> resolveSelection(apiId, generation.getApp()))
                .toList();
        List<TestCaseApiPayload> apis = selections.stream()
                .map(selection -> {
                    String apiId = String.valueOf(selection.sourceApiId());
                    responseApiIdToSourceApiId.put(apiId, selection.sourceApiId());
                    responseApiIdToSourceApiId.put(endpointId(selection.endpoint()), selection.sourceApiId());
                    return toTestCaseApiPayload(apiId, selection.endpoint(), selection.inventory());
                })
                .toList();
        List<TestCaseApiPayload> domainApis = domainApis(selections, generation.getApp());

        List<ExistingTestCasePayload> existingTestCases = existingTestCases(selections);
        TestCaseGeneratorRequest request = new TestCaseGeneratorRequest(
                AGENT,
                UUID.randomUUID().toString(),
                generation.getRequestedBy(),
                new ProjectPayload(resolveProjectId(selections, generation.getApp().getId()),
                        String.valueOf(generation.getApp().getId()),
                        generation.getApp().getName()),
                toEnvironmentPayload(generation, sourceEnvironment),
                new MetadataPayload("ko", LocalDateTime.now(), "INTERNAL"),
                new TestGenerationContext(
                        String.valueOf(generation.getId()),
                        "SELECTED_APIS",
                        resolveTestLevel(generation, sourceEnvironment),
                        toCoverageRatio(generation.getCurrentCoverage()),
                        resolveTargetCoverage(generation),
                        generation.getContextSummary()
                ),
                apis,
                domainApis,
                existingTestCases,
                null
        );
        log.debug("AI test case generator request payload. generationId={}, payload={}",
                generation.getId(),
                toJsonForLog(request));
        TestCaseGeneratorResponse response = generateTestCaseDrafts(request);
        log.debug("AI test case generator response payload. generationId={}, payload={}",
                generation.getId(),
                toJsonForLog(response));

        List<AiGeneratedDraftCommand> commands = toCommands(response, responseApiIdToSourceApiId);
        if (commands.isEmpty()) {
            log.warn("AI test case generator returned 0 drafts. generationId={}, responseRequestId={}, responseDraftsNull={}, apiSummaries={}, existingTestCaseCount={}, contextSummaryLength={}, projectId={}, environmentId={}, environmentBaseUrlPresent={}",
                    generation.getId(),
                    response == null ? "null" : response.requestId(),
                    response == null || response.drafts() == null,
                    summarizeApis(apis),
                    existingTestCases.size(),
                    generation.getContextSummary() == null ? 0 : generation.getContextSummary().length(),
                    request.project() == null ? null : request.project().projectId(),
                    request.environment() == null ? null : request.environment().environmentId(),
                    request.environment() != null
                            && request.environment().baseUrl() != null
                            && !request.environment().baseUrl().isBlank());
        } else {
            log.info("AI test case generator completed. generationId={}, draftCount={}",
                    generation.getId(),
                    commands.size());
        }
        return commands;
    }

    @Override
    public List<AiGeneratedDraftCommand> generateDraftsFromFailure(TestGeneration generation, Execution execution, ExecutionStepLog failedLog) {
        throw new ApiException(ErrorCode.INVALID_INPUT, "FROM_FAILURE mode is not yet supported.");
    }

    private ApiSelection resolveSelection(Long apiId, App app) {
        ApiInventory inventory = apiInventoryRepository.findById(apiId).orElse(null);
        if (inventory == null) {
            ApiEndpoint endpoint = apiEndpointService.getApiEndpoint(apiId);
            return new ApiSelection(apiId, endpoint, null);
        }
        ApiEndpoint endpoint = apiEndpointService.findOrCreateFromInventory(app, inventory);
        return new ApiSelection(apiId, endpoint, inventory);
    }

    private TestCaseApiPayload toTestCaseApiPayload(String apiId, ApiEndpoint endpoint, ApiInventory inventory) {
        JsonNode requestSchema = meaningfulJsonOrNull(parseJson(inventory == null ? endpoint.getRequestSchema() : inventory.getRequestSchema()));
        JsonNode responseSchema = meaningfulJsonOrNull(parseJson(inventory == null ? endpoint.getResponseSchema() : inventory.getResponseSchema()));
        return new TestCaseApiPayload(
                apiId,           // apiId
                endpoint.getMethod().name(),
                endpoint.getPath(),
                endpoint.getDomainTag(),
                requestSchema,
                responseSchema,
                integerArray(responseSchema, "expectedStatusCodes"),
                integerArray(responseSchema, "errorStatusCodes"),
                errorCodes(responseSchema),
                inventory == null ? null : inventory.isAuthRequired(),
                endpoint.isDeprecated()
        );
    }

    private List<TestCaseApiPayload> domainApis(List<ApiSelection> selections, App app) {
        if (app == null || app.getId() == null) {
            return List.of();
        }
        LinkedHashSet<String> domainTags = new LinkedHashSet<>();
        for (ApiSelection selection : selections) {
            String domainTag = selection.endpoint().getDomainTag();
            if (domainTag != null && !domainTag.isBlank()) {
                domainTags.add(domainTag);
            }
        }
        if (domainTags.isEmpty()) {
            return selections.stream()
                    .map(selection -> toTestCaseApiPayload(
                            String.valueOf(selection.sourceApiId()),
                            selection.endpoint(),
                            selection.inventory()))
                    .toList();
        }
        Map<String, TestCaseApiPayload> payloads = new LinkedHashMap<>();
        for (String domainTag : domainTags) {
            List<ApiEndpoint> endpoints = apiEndpointService.getApiEndpointsByDomain(app.getId(), domainTag);
            if (endpoints == null || endpoints.isEmpty()) {
                continue;
            }
            for (ApiEndpoint endpoint : endpoints) {
                payloads.putIfAbsent(endpointId(endpoint), toTestCaseApiPayload(String.valueOf(endpoint.getId()), endpoint, null));
            }
        }
        return List.copyOf(payloads.values());
    }

    private TestCaseGeneratorResponse generateTestCaseDrafts(TestCaseGeneratorRequest request) {
        return aiClient.generateTestCaseDrafts(request);
    }

    private List<ExistingTestCasePayload> existingTestCases(List<ApiSelection> selections) {
        List<TestCase> testCases = new ArrayList<>();
        LinkedHashSet<Long> endpointIds = new LinkedHashSet<>();
        for (ApiSelection selection : selections) {
            endpointIds.add(selection.endpoint().getId());
            if (selection.inventory() != null) {
                testCases.addAll(testCaseRepository.findTop3ByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(selection.inventory().getId()));
            }
        }
        if (!endpointIds.isEmpty()) {
            testCases.addAll(testCaseRepository.findByApiEndpointIdInAndActiveTrueOrderByUpdatedAtDesc(List.copyOf(endpointIds)));
        }
        return testCases.stream()
                .distinct()
                .limit(20)
                .map(testCase -> new ExistingTestCasePayload(
                        String.valueOf(testCase.getId()),
                        String.valueOf(testCase.getApiEndpoint().getId()),
                        testCase.getName(),
                        testCase.getType().name(),
                        testCase.getTestLevel().name(),
                        meaningfulJsonOrNull(parseJson(testCase.getRequestSpec())),
                        meaningfulJsonOrNull(parseJson(testCase.getExpectedSpec())),
                        meaningfulJsonOrNull(parseJson(testCase.getAssertionSpec()))
                ))
                .toList();
    }

    private AiGeneratedDraftCommand toCommand(TestCaseDraftPayload draft, Map<String, Long> responseApiIdToSourceApiId) {
        Long apiId = null;
        if (draft.apiId() != null) {
            apiId = responseApiIdToSourceApiId.get(draft.apiId());
            if (apiId == null) {
                apiId = parseLongOrNull(draft.apiId());
            }
        }
        if (apiId == null && draft.endpoint_id() != null) {
            apiId = responseApiIdToSourceApiId.get(draft.endpoint_id());
        }
        if (apiId == null) {
            throw new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "AI response did not include a resolvable apiId or endpoint_id.");
        }
        return new AiGeneratedDraftCommand(
                apiId,
                draft.title(),
                draft.description(),
                draft.type(),
                draft.risk_level(),
                draft.userRole(),
                draft.stateCondition(),
                draft.dataVariant(),
                jsonToStorageText(mergeExecutionTarget(draft.requestSpec(), draft.execution_endpoint(), draft.execution_method())),
                jsonToStorageText(draft.expectedSpec()),
                jsonToStorageText(draft.assertionSpec()),
                draft.duplicate()
        );
    }

    private JsonNode mergeExecutionTarget(JsonNode requestSpec, String executionEndpoint, String executionMethod) {
        if ((executionEndpoint == null || executionEndpoint.isBlank())
                && (executionMethod == null || executionMethod.isBlank())) {
            return requestSpec;
        }
        ObjectNode target = requestSpec != null && requestSpec.isObject()
                ? requestSpec.deepCopy()
                : objectMapper.createObjectNode();
        if (executionEndpoint != null && !executionEndpoint.isBlank()
                && !target.has("endpoint") && !target.has("path")) {
            target.put("endpoint", executionEndpoint.trim());
        }
        if (executionMethod != null && !executionMethod.isBlank()
                && !target.has("method")) {
            target.put("method", executionMethod.trim().toUpperCase());
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

    private List<AiGeneratedDraftCommand> toCommands(TestCaseGeneratorResponse response, Map<String, Long> responseApiIdToSourceApiId) {
        if (response == null || response.drafts() == null) {
            return List.of();
        }
        return response.drafts().stream()
                .map(draft -> toCommand(draft, responseApiIdToSourceApiId))
                .toList();
    }

    private EnvironmentPayload toEnvironmentPayload(TestGeneration generation, Environment sourceEnvironment) {
        Environment environment = sourceEnvironment == null ? generation.getEnvironment() : sourceEnvironment;
        if (environment == null) {
            App app = generation.getApp();
            return new EnvironmentPayload(
                    "app-" + app.getId(),
                    app.getName() + " app scope",
                    "",
                    "REGRESSION",
                    null,
                    objectMapper.nullNode(),
                    objectMapper.nullNode()
            );
        }
        return new EnvironmentPayload(
                String.valueOf(environment.getId()),
                environment.getName(),
                environment.getBaseUrl(),
                environment.getDefaultTestLevel() == null ? null : environment.getDefaultTestLevel().name(),
                environment.getAuthType() == null ? null : environment.getAuthType().name(),
                meaningfulJsonOrNull(parseJson(environment.getAuthConfig())),
                meaningfulJsonOrNull(parseJson(environment.getHeaders()))
        );
    }

    private Double resolveTargetCoverage(TestGeneration generation) {
        Double currentCoverage = toCoverageRatio(generation.getCurrentCoverage());
        if (currentCoverage == null) {
            return 0.8;
        }
        return Math.min(1.0, currentCoverage + 0.1);
    }

    private String resolveTestLevel(TestGeneration generation, Environment sourceEnvironment) {
        Environment environment = sourceEnvironment == null ? generation.getEnvironment() : sourceEnvironment;
        if (environment == null || environment.getDefaultTestLevel() == null) {
            return "REGRESSION";
        }
        return environment.getDefaultTestLevel().name();
    }

    private String resolveProjectId(List<ApiSelection> selections, Long fallbackProjectId) {
        return selections.stream()
                .map(ApiSelection::inventory)
                .filter(java.util.Objects::nonNull)
                .map(ApiInventory::getProject)
                .filter(java.util.Objects::nonNull)
                .map(project -> String.valueOf(project.getId()))
                .findFirst()
                .orElseGet(() -> String.valueOf(fallbackProjectId));
    }

    private String endpointId(ApiEndpoint endpoint) {
        return endpoint.getMethod().name() + ":" + endpoint.getPath();
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.getNodeFactory().textNode(value);
        }
    }

    private JsonNode meaningfulJsonOrNull(JsonNode value) {
        return hasMeaningfulJson(value) ? value : objectMapper.nullNode();
    }

    private List<Integer> integerArray(JsonNode root, String fieldName) {
        if (root == null || !root.has(fieldName) || !root.get(fieldName).isArray()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        root.get(fieldName).forEach(value -> {
            if (value.canConvertToInt()) {
                values.add(value.intValue());
            }
        });
        return values;
    }

    private List<String> errorCodes(JsonNode responseSchema) {
        if (responseSchema == null || !responseSchema.has("responses") || !responseSchema.get("responses").isArray()) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        responseSchema.get("responses").forEach(response -> {
            JsonNode sampleBody = response.path("sampleBody");
            addTextField(codes, sampleBody, "errorCode");
            addTextField(codes, sampleBody, "code");
        });
        return List.copyOf(codes);
    }

    private void addTextField(LinkedHashSet<String> values, JsonNode node, String fieldName) {
        if (node != null && node.hasNonNull(fieldName) && node.get(fieldName).isTextual()) {
            values.add(node.get(fieldName).asText());
        }
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

    private Long parseLongOrNull(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private Double toCoverageRatio(java.math.BigDecimal value) {
        Double coverage = toDouble(value);
        if (coverage == null) {
            return null;
        }
        return coverage > 1.0 ? coverage / 100.0 : coverage;
    }

    private List<String> summarizeApis(List<TestCaseApiPayload> apis) {
        return apis.stream()
                .map(api -> "%s %s apiId=%s endpointId=%s requestSchema=%s responseSchema=%s authRequired=%s deprecated=%s".formatted(
                        api.method(),
                        api.path(),
                        api.apiId(),
                        endpointIdFromApi(api),
                        hasMeaningfulJson(api.requestSchema()),
                        hasMeaningfulJson(api.responseSchema()),
                        api.authRequired(),
                        api.deprecated()
                ))
                .toList();
    }

    private boolean hasMeaningfulJson(JsonNode value) {
        return value != null
                && !value.isNull()
                && !value.isMissingNode()
                && !(value.isObject() && value.isEmpty())
                && !(value.isArray() && value.isEmpty())
                && !(value.isTextual() && value.asText().isBlank());
    }

    private String endpointIdFromApi(TestCaseApiPayload api) {
        return api.method() + ":" + api.path();
    }

    private String toJsonForLog(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return compact(objectMapper.writeValueAsString(value));
        } catch (Exception exception) {
            return "<unserializable:%s>".formatted(exception.getClass().getSimpleName());
        }
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) {
            return "<empty>";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        return compacted.length() > 4000 ? compacted.substring(0, 4000) + "..." : compacted;
    }

    private record ApiSelection(
            Long sourceApiId,
            ApiEndpoint endpoint,
            ApiInventory inventory
    ) {
    }
}
