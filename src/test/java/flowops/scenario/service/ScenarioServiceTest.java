package flowops.scenario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.aiintegration.client.AiClient;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionStepLogRepository;
import flowops.global.config.ExternalServiceProperties;
import flowops.scenario.domain.entity.Scenario;
import flowops.scenario.domain.entity.ScenarioSource;
import flowops.scenario.domain.entity.ScenarioType;
import flowops.scenario.domain.entity.ScenarioStep;
import flowops.scenario.dto.request.CreateScenarioRequest;
import flowops.scenario.dto.request.ScenarioStepRequest;
import flowops.scenario.dto.response.ScenarioDetailResponse;
import flowops.scenario.repository.ScenarioRepository;
import flowops.scenario.repository.ScenarioStepRepository;
import flowops.testcase.repository.TestCaseRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ScenarioServiceTest {

    @Mock
    private ScenarioRepository scenarioRepository;
    @Mock
    private ScenarioStepRepository scenarioStepRepository;
    @Mock
    private AppService appService;
    @Mock
    private ApiEndpointService apiEndpointService;
    @Mock
    private ApiEndpointRepository apiEndpointRepository;
    @Mock
    private ApiInventoryRepository apiInventoryRepository;
    @Mock
    private AiClient aiClient;
    @Mock
    private ExternalServiceProperties externalServiceProperties;
    @Mock
    private ExecutionStepLogRepository executionStepLogRepository;
    @Mock
    private EnvironmentRepository environmentRepository;
    @Mock
    private TestCaseRepository testCaseRepository;

    @InjectMocks
    private ScenarioService scenarioService;

    @Test
    void createPersistsEnvironmentFromRequest() {
        ReflectionTestUtils.setField(scenarioService, "objectMapper", new ObjectMapper().findAndRegisterModules());
        App app = app(1L);
        Environment environment = environment(3L, app);
        ApiEndpoint endpoint = endpoint(101L, app);
        when(appService.getApp(1L)).thenReturn(app);
        when(environmentRepository.findById(3L)).thenReturn(Optional.of(environment));
        when(apiInventoryRepository.findById(101L)).thenReturn(Optional.empty());
        when(apiEndpointService.getApiEndpoint(101L)).thenReturn(endpoint);
        when(scenarioRepository.save(any(Scenario.class))).thenAnswer(invocation -> {
            Scenario scenario = invocation.getArgument(0);
            ReflectionTestUtils.setField(scenario, "id", 501L);
            return scenario;
        });
        when(scenarioStepRepository.save(any(ScenarioStep.class))).thenAnswer(invocation -> {
            ScenarioStep step = invocation.getArgument(0);
            ReflectionTestUtils.setField(step, "id", 601L);
            return step;
        });

        ScenarioDetailResponse response = scenarioService.create(new CreateScenarioRequest(
                1L,
                3L,
                "Order flow",
                null,
                ScenarioType.HAPPY_PATH,
                null,
                ScenarioSource.AI,
                List.of(new ScenarioStepRequest(
                        null,
                        1,
                        101L,
                        "Create order",
                        "step-1",
                        "step_1",
                        null,
                        "HAPPY_PATH",
                        "REGRESSION",
                        "CUSTOMER",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null
                ))
        ));

        assertThat(response.environmentId()).isEqualTo(3L);
        assertThat(response.steps()).hasSize(1);
    }

    private App app(Long id) {
        App app = App.builder()
                .name("FlowOps")
                .build();
        ReflectionTestUtils.setField(app, "id", id);
        return app;
    }

    private Environment environment(Long id, App app) {
        Environment environment = Environment.builder()
                .app(app)
                .name("dev")
                .baseUrl("https://api.example.com")
                .build();
        ReflectionTestUtils.setField(environment, "id", id);
        return environment;
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
