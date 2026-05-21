package flowops.testgeneration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.service.EnvironmentService;
import flowops.global.exception.ApiException;
import flowops.integration.ai.AiGeneratedDraftCommand;
import flowops.integration.ai.AiTestGenerationGateway;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testgeneration.domain.entity.GeneratedTestCaseDraft;
import flowops.testgeneration.domain.entity.TestGeneration;
import flowops.testgeneration.domain.entity.TestGenerationStatus;
import flowops.testgeneration.dto.request.SaveGeneratedDraftsRequest;
import flowops.testgeneration.dto.request.SaveGeneratedDraftsRequest.TestCaseDraftSaveRequest;
import flowops.testgeneration.repository.GeneratedTestCaseDraftRepository;
import flowops.testgeneration.repository.TestGenerationApiSelectionRepository;
import flowops.testgeneration.repository.TestGenerationRepository;
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
class TestGenerationServiceTest {

    @Mock
    private TestGenerationRepository testGenerationRepository;

    @Mock
    private TestGenerationApiSelectionRepository selectionRepository;

    @Mock
    private GeneratedTestCaseDraftRepository draftRepository;

    @Mock
    private AppService appService;

    @Mock
    private ApiEndpointService apiEndpointService;

    @Mock
    private ApiInventoryRepository apiInventoryRepository;

    @Mock
    private AiTestGenerationGateway aiTestGenerationGateway;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private EnvironmentService environmentService;

    @Test
    void saveDraftsUsesGenerationAppAsSourceOfTruth() {
        App generationApp = app(1L);
        TestGeneration generation = generation(77L, generationApp);
        ApiEndpoint endpoint = endpoint(10L, generationApp);
        GeneratedTestCaseDraft draft = draft(1001L, generation, endpoint);

        when(testGenerationRepository.findById(77L)).thenReturn(Optional.of(generation));
        when(draftRepository.findByGenerationIdAndIdIn(77L, List.of(1001L))).thenReturn(List.of(draft));
        when(testCaseRepository.save(any(TestCase.class))).thenAnswer(invocation -> {
            TestCase testCase = invocation.getArgument(0);
            ReflectionTestUtils.setField(testCase, "id", 501L);
            return testCase;
        });

        service().saveDrafts(77L, saveDraftsRequest(null));

        ArgumentCaptor<TestCase> captor = ArgumentCaptor.forClass(TestCase.class);
        verify(testCaseRepository).save(captor.capture());
        assertThat(captor.getValue().getApp()).isSameAs(generationApp);
        assertThat(captor.getValue().getApiEndpoint()).isSameAs(endpoint);
    }

    @Test
    void saveDraftsRejectsRequestAppIdThatDiffersFromGenerationApp() {
        App generationApp = app(1L);
        TestGeneration generation = generation(77L, generationApp);
        when(testGenerationRepository.findById(77L)).thenReturn(Optional.of(generation));

        assertThatThrownBy(() -> service().saveDrafts(77L, saveDraftsRequest(2L)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Requested appId does not match the generation appId.");

        verify(testCaseRepository, never()).save(any());
    }

    @Test
    void generateDraftsIgnoresAiDuplicateFlagWhenNoExistingTitleMatches() {
        App generationApp = app(1L);
        TestGeneration generation = generation(77L, generationApp);
        ApiEndpoint endpoint = endpoint(10L, generationApp);

        when(testGenerationRepository.findById(77L)).thenReturn(Optional.of(generation));
        when(aiTestGenerationGateway.generateDrafts(generation, List.of(10L), null))
                .thenReturn(List.of(command(10L, "Reject request with invalid authentication token", true)));
        when(apiInventoryRepository.findById(10L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(10L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(10L))
                .thenReturn(List.of(testCase(generationApp, endpoint, "Reject request with missing authentication token")));
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().generateDraftsAsync(77L, List.of(10L), null);

        ArgumentCaptor<GeneratedTestCaseDraft> captor = ArgumentCaptor.forClass(GeneratedTestCaseDraft.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().isDuplicate()).isFalse();
        assertThat(generation.getDuplicateCount()).isZero();
        assertThat(generation.getNewCount()).isEqualTo(1);
    }

    @Test
    void generateDraftsMarksDuplicateOnlyWhenExistingTitleMatches() {
        App generationApp = app(1L);
        TestGeneration generation = generation(77L, generationApp);
        ApiEndpoint endpoint = endpoint(10L, generationApp);
        String title = "Reject request with invalid authentication token";

        when(testGenerationRepository.findById(77L)).thenReturn(Optional.of(generation));
        when(aiTestGenerationGateway.generateDrafts(generation, List.of(10L), null))
                .thenReturn(List.of(command(10L, title, false)));
        when(apiInventoryRepository.findById(10L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(10L)).thenReturn(endpoint);
        when(testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(10L))
                .thenReturn(List.of(testCase(generationApp, endpoint, title)));
        when(draftRepository.save(any(GeneratedTestCaseDraft.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service().generateDraftsAsync(77L, List.of(10L), null);

        ArgumentCaptor<GeneratedTestCaseDraft> captor = ArgumentCaptor.forClass(GeneratedTestCaseDraft.class);
        verify(draftRepository).save(captor.capture());
        assertThat(captor.getValue().isDuplicate()).isTrue();
        assertThat(generation.getDuplicateCount()).isEqualTo(1);
        assertThat(generation.getNewCount()).isZero();
    }

    private TestGenerationService service() {
        return new TestGenerationService(
                testGenerationRepository,
                selectionRepository,
                draftRepository,
                appService,
                apiEndpointService,
                apiInventoryRepository,
                aiTestGenerationGateway,
                testCaseRepository,
                environmentService
        );
    }

    private SaveGeneratedDraftsRequest saveDraftsRequest(Long appId) {
        return new SaveGeneratedDraftsRequest(
                appId,
                List.of(new TestCaseDraftSaveRequest(
                        1001L,
                        "Order creation succeeds",
                        "Verifies order creation.",
                        "HAPPY_PATH",
                        "REGRESSION",
                        "CUSTOMER",
                        "Signed in",
                        "single product",
                        "{\"body\":{\"productId\":1}}",
                        "{\"status\":201}",
                        "{\"assertions\":[\"status == 201\"]}"
                ))
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
                .status(TestGenerationStatus.COMPLETED)
                .requestedBy("tester")
                .existingCount(0)
                .newCount(1)
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
                .deprecated(false)
                .build();
        ReflectionTestUtils.setField(endpoint, "id", id);
        return endpoint;
    }

    private GeneratedTestCaseDraft draft(Long id, TestGeneration generation, ApiEndpoint endpoint) {
        GeneratedTestCaseDraft draft = GeneratedTestCaseDraft.builder()
                .generation(generation)
                .apiEndpoint(endpoint)
                .title("Order creation succeeds")
                .description("Verifies order creation.")
                .type("HAPPY_PATH")
                .userRole("CUSTOMER")
                .stateCondition("Signed in")
                .dataVariant("single product")
                .requestSpec("{\"body\":{\"productId\":1}}")
                .expectedSpec("{\"status\":201}")
                .assertionSpec("{\"assertions\":[\"status == 201\"]}")
                .duplicate(false)
                .selectedForSave(false)
                .createdAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(draft, "id", id);
        return draft;
    }

    private AiGeneratedDraftCommand command(Long apiId, String title, boolean duplicate) {
        return new AiGeneratedDraftCommand(
                apiId,
                title,
                "Generated by AI.",
                "AUTHORIZATION",
                "CUSTOMER",
                "Signed in",
                "invalid token",
                "{\"headers\":{\"Authorization\":\"Bearer invalid\"}}",
                "{\"status\":401}",
                "{\"assertions\":[\"status == 401\"]}",
                duplicate
        );
    }

    private TestCase testCase(App app, ApiEndpoint endpoint, String name) {
        return TestCase.builder()
                .app(app)
                .apiEndpoint(endpoint)
                .name(name)
                .description("Existing saved test case.")
                .type(TestCaseType.AUTHORIZATION)
                .testLevel(flowops.testcase.domain.entity.TestLevel.REGRESSION)
                .source(TestCaseSource.AUTO)
                .requestSpec("{}")
                .expectedSpec("{}")
                .assertionSpec("{}")
                .active(true)
                .version(1)
                .build();
    }
}
