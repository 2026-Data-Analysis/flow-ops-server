package flowops.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.environment.domain.entity.Environment;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.integration.ai.AiAgentContracts.ApiPayload;
import flowops.integration.ai.AiAgentContracts.EnvironmentPayload;
import flowops.integration.ai.AiAgentContracts.ExistingTestCasePayload;
import flowops.integration.ai.AiAgentContracts.MetadataPayload;
import flowops.integration.ai.AiAgentContracts.ProjectPayload;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "external.ai", name = "mock-enabled", havingValue = "false")
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
    public List<AiGeneratedDraftCommand> generateDrafts(TestGeneration generation, List<Long> apiIds) {
        Map<String, Long> endpointIdToApiId = new LinkedHashMap<>();
        List<ApiSelection> selections = apiIds.stream()
                .map(this::resolveSelection)
                .toList();
        List<ApiPayload> apis = selections.stream()
                .map(selection -> {
                    String endpointId = endpointId(selection.endpoint());
                    endpointIdToApiId.put(endpointId, selection.sourceApiId());
                    return toApiPayload(endpointId, selection.endpoint(), selection.inventory());
                })
                .toList();

        TestCaseGeneratorResponse response = aiClient.generateTestCaseDrafts(new TestCaseGeneratorRequest(
                AGENT,
                UUID.randomUUID().toString(),
                generation.getRequestedBy(),
                new ProjectPayload(null, generation.getApp().getId(), generation.getApp().getName()),
                toEnvironmentPayload(generation.getEnvironment()),
                new MetadataPayload("ko", LocalDateTime.now(), "INTERNAL"),
                new TestGenerationContext(
                        generation.getId(),
                        "SELECTED_APIS",
                        resolveTestLevel(generation),
                        toDouble(generation.getCurrentCoverage()),
                        null,
                        generation.getContextSummary()
                ),
                apis,
                existingTestCases(selections),
                null
        ));

        if (response == null || response.drafts() == null) {
            return List.of();
        }
        return response.drafts().stream()
                .map(draft -> toCommand(draft, endpointIdToApiId))
                .toList();
    }

    private ApiSelection resolveSelection(Long apiId) {
        ApiInventory inventory = apiInventoryRepository.findById(apiId).orElse(null);
        if (inventory == null) {
            ApiEndpoint endpoint = apiEndpointService.getApiEndpoint(apiId);
            return new ApiSelection(apiId, endpoint, null);
        }
        ApiEndpoint endpoint = apiEndpointService.findFirstByMethodAndPath(
                ApiMethod.valueOf(inventory.getMethod().name()),
                inventory.getEndpointPath()
        );
        return new ApiSelection(apiId, endpoint, inventory);
    }

    private ApiPayload toApiPayload(String endpointId, ApiEndpoint endpoint, ApiInventory inventory) {
        return new ApiPayload(
                endpointId,
                endpoint.getMethod().name(),
                endpoint.getPath(),
                endpoint.getDomainTag(),
                parseJson(endpoint.getRequestSchema()),
                parseJson(endpoint.getResponseSchema()),
                inventory == null ? null : inventory.isAuthRequired(),
                endpoint.isDeprecated(),
                null
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
                        testCase.getId(),
                        testCase.getApiEndpoint().getId(),
                        testCase.getName(),
                        testCase.getType().name(),
                        testCase.getTestLevel().name(),
                        testCase.getRequestSpec(),
                        testCase.getExpectedSpec(),
                        testCase.getAssertionSpec()
                ))
                .toList();
    }

    private AiGeneratedDraftCommand toCommand(TestCaseDraftPayload draft, Map<String, Long> endpointIdToApiId) {
        Long apiId = draft.apiId();
        if (apiId == null && draft.endpoint_id() != null) {
            apiId = endpointIdToApiId.get(draft.endpoint_id());
        }
        if (apiId == null) {
            throw new ApiException(ErrorCode.EXTERNAL_SERVICE_ERROR, "AI response did not include a resolvable apiId or endpoint_id.");
        }
        return new AiGeneratedDraftCommand(
                apiId,
                draft.title(),
                draft.description(),
                draft.type(),
                draft.userRole(),
                draft.stateCondition(),
                draft.dataVariant(),
                draft.requestSpec(),
                draft.expectedSpec(),
                draft.assertionSpec(),
                draft.duplicate()
        );
    }

    private EnvironmentPayload toEnvironmentPayload(Environment environment) {
        if (environment == null) {
            return null;
        }
        return new EnvironmentPayload(
                environment.getId(),
                environment.getName(),
                environment.getBaseUrl(),
                environment.getDefaultTestLevel() == null ? null : environment.getDefaultTestLevel().name()
        );
    }

    private String resolveTestLevel(TestGeneration generation) {
        if (generation.getEnvironment() == null || generation.getEnvironment().getDefaultTestLevel() == null) {
            return "REGRESSION";
        }
        return generation.getEnvironment().getDefaultTestLevel().name();
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
