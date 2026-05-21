package flowops.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
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
import flowops.integration.ai.AiAgentContracts.FailureContextPayload;
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

        TestCaseGeneratorResponse response = aiClient.generateTestCaseDrafts(new TestCaseGeneratorRequest(
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
                        toDouble(generation.getCurrentCoverage()),
                        resolveTargetCoverage(generation),
                        generation.getContextSummary()
                ),
                apis,
                existingTestCases(selections),
                null
        ));

        List<AiGeneratedDraftCommand> commands = toCommands(response, responseApiIdToSourceApiId);
        if (commands.isEmpty()) {
            log.warn("AI test case generator returned 0 drafts. generationId={}, responseRequestId={}, responseDraftsNull={}",
                    generation.getId(),
                    response == null ? "null" : response.requestId(),
                    response == null || response.drafts() == null);
        } else {
            log.info("AI test case generator completed. generationId={}, draftCount={}",
                    generation.getId(),
                    commands.size());
        }
        return commands;
    }

    @Override
    public List<AiGeneratedDraftCommand> generateDraftsFromFailure(TestGeneration generation, Execution execution, ExecutionStepLog failedLog) {
        log.info("Calling AI failure test generator. generationId={}, executionId={}, failedLogId={}",
                generation.getId(),
                execution.getId(),
                failedLog.getId());
        ApiEndpoint endpoint = resolveEndpoint(failedLog);
        String apiId = String.valueOf(endpoint.getId());
        String endpointId = endpointId(endpoint);
        Map<String, Long> responseApiIdToSourceApiId = Map.of(
                apiId, endpoint.getId(),
                endpointId, endpoint.getId()
        );
        ApiInventory inventory = failedLog.getTestCase() == null ? null : failedLog.getTestCase().getApiInventory();

        TestCaseGeneratorResponse response = aiClient.generateTestCaseDrafts(new TestCaseGeneratorRequest(
                AGENT,
                UUID.randomUUID().toString(),
                generation.getRequestedBy(),
                new ProjectPayload(resolveProjectId(List.of(new ApiSelection(endpoint.getId(), endpoint, inventory)), generation.getApp().getId()),
                        String.valueOf(generation.getApp().getId()),
                        generation.getApp().getName()),
                toEnvironmentPayload(generation, execution.getEnvironment()),
                new MetadataPayload("ko", LocalDateTime.now(), "INTERNAL"),
                new TestGenerationContext(
                        String.valueOf(generation.getId()),
                        "FROM_FAILURE",
                        resolveTestLevel(generation, execution.getEnvironment()),
                        toDouble(generation.getCurrentCoverage()),
                        resolveTargetCoverage(generation),
                        generation.getContextSummary()
                ),
                List.of(toTestCaseApiPayload(apiId, endpoint, inventory)),
                existingTestCases(List.of(new ApiSelection(endpoint.getId(), endpoint, inventory))),
                new FailureContextPayload(
                        execution.getId(),
                        failedLog.getId(),
                        failedLog.getResponseCode(),
                        failedLog.getRequestBody(),
                        failedLog.getResponseBody(),
                        failedLog.getErrorMessage(),
                        expectedBehavior(failedLog),
                        actualBehavior(failedLog)
                )
        ));

        List<AiGeneratedDraftCommand> commands = toCommands(response, responseApiIdToSourceApiId);
        log.info("AI failure test generator completed. generationId={}, draftCount={}",
                generation.getId(),
                commands.size());
        return commands;
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
        return new TestCaseApiPayload(
                apiId,           // apiId
                apiId,           // endpoint_id
                endpoint.getMethod().name(),
                endpoint.getPath(),
                endpoint.getDomainTag(),
                parseJson(inventory == null ? endpoint.getRequestSchema() : inventory.getRequestSchema()),
                parseJson(inventory == null ? endpoint.getResponseSchema() : inventory.getResponseSchema()),
                inventory == null ? null : inventory.isAuthRequired(),
                endpoint.isDeprecated()
        );
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
                        parseJson(testCase.getRequestSpec()),
                        parseJson(testCase.getExpectedSpec()),
                        parseJson(testCase.getAssertionSpec())
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
        String type = draft.type() != null ? draft.type() : draft.test_case_type();
        return new AiGeneratedDraftCommand(
                apiId,
                draft.title(),
                draft.description(),
                type,
                draft.userRole(),
                draft.stateCondition(),
                draft.dataVariant(),
                jsonToStorageText(draft.requestSpec()),
                jsonToStorageText(draft.expectedSpec()),
                jsonToStorageText(draft.assertionSpec()),
                draft.duplicate()
        );
    }

    private List<AiGeneratedDraftCommand> toCommands(TestCaseGeneratorResponse response, Map<String, Long> responseApiIdToSourceApiId) {
        if (response == null || response.drafts() == null) {
            return List.of();
        }
        return response.drafts().stream()
                .map(draft -> toCommand(draft, responseApiIdToSourceApiId))
                .toList();
    }

    private ApiEndpoint resolveEndpoint(ExecutionStepLog failedLog) {
        if (failedLog.getTestCase() != null) {
            return failedLog.getTestCase().getApiEndpoint();
        }
        if (failedLog.getScenarioStep() != null) {
            return failedLog.getScenarioStep().getApiEndpoint();
        }
        return apiEndpointService.findFirstByMethodAndPath(ApiMethod.valueOf(failedLog.getMethod()), failedLog.getPath());
    }

    private String expectedBehavior(ExecutionStepLog failedLog) {
        if (failedLog.getTestCase() != null && failedLog.getTestCase().getExpectedSpec() != null) {
            return failedLog.getTestCase().getExpectedSpec();
        }
        if (failedLog.getScenarioStep() != null && failedLog.getScenarioStep().getValidationRules() != null) {
            return failedLog.getScenarioStep().getValidationRules();
        }
        return "{\"status\":200}";
    }

    private String actualBehavior(ExecutionStepLog failedLog) {
        return """
                {
                  "statusCode": %s,
                  "responseBody": %s
                }
                """.formatted(
                failedLog.getResponseCode() == null ? "null" : failedLog.getResponseCode(),
                toJsonString(failedLog.getResponseBody())
        ).trim();
    }

    private String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }

    private EnvironmentPayload toEnvironmentPayload(TestGeneration generation, Environment sourceEnvironment) {
        Environment environment = sourceEnvironment == null ? generation.getEnvironment() : sourceEnvironment;
        if (environment == null) {
            App app = generation.getApp();
            return new EnvironmentPayload(
                    "app-" + app.getId(),
                    app.getName() + " app scope",
                    "",
                    "REGRESSION"
            );
        }
        return new EnvironmentPayload(
                String.valueOf(environment.getId()),
                environment.getName(),
                environment.getBaseUrl(),
                environment.getDefaultTestLevel() == null ? null : environment.getDefaultTestLevel().name()
        );
    }

    private Double resolveTargetCoverage(TestGeneration generation) {
        Double currentCoverage = toDouble(generation.getCurrentCoverage());
        if (currentCoverage == null) {
            return 100.0;
        }
        return Math.min(100.0, currentCoverage + 10.0);
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

    private record ApiSelection(
            Long sourceApiId,
            ApiEndpoint endpoint,
            ApiInventory inventory
    ) {
    }
}
