package flowops.environment.service;

import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.repository.ApiEndpointRepository;
import flowops.app.domain.entity.App;
import flowops.app.service.AppService;
import flowops.coverage.service.CoverageService;
import flowops.environment.domain.entity.Environment;
import flowops.environment.domain.entity.TestLevelSource;
import flowops.environment.dto.request.CreateEnvironmentRequest;
import flowops.environment.dto.request.UpdateEnvironmentRequest;
import flowops.environment.dto.response.ConnectionTestResponse;
import flowops.environment.dto.response.EnvironmentResponse;
import flowops.environment.repository.EnvironmentRepository;
import flowops.execution.repository.ExecutionRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 실행 환경의 생성, 목록 조회, 수정, 연결 테스트를 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class EnvironmentService {

    private static final List<String> CONNECTION_PROBE_PATHS = List.of(
            "/hello",
            "/up",
            "/actuator/health",
            "/actuator/info",
            "/health"
    );

    private final EnvironmentRepository environmentRepository;
    private final AppService appService;
    private final EnvironmentSecretService environmentSecretService;
    private final ExecutionRepository executionRepository;
    private final ApiEndpointRepository apiEndpointRepository;
    private final CoverageService coverageService;
    private final WebClient.Builder webClientBuilder;

    @Transactional
    public EnvironmentResponse createEnvironment(Long appId, CreateEnvironmentRequest request) {
        App app = appService.getApp(appId);
        Environment environment = environmentRepository.save(Environment.builder()
                .app(app)
                .name(request.name())
                .branchName(request.branchName())
                .baseUrl(request.baseUrl())
                .authType(request.authType())
                .authConfig(environmentSecretService.protect(request.authType(), request.authConfig()))
                .headers(request.headers())
                .defaultTestLevel(request.defaultTestLevel())
                .defaultTestLevelSource(defaultSource(request.defaultTestLevelSource()))
                .build());
        return toResponse(environment);
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> listByApp(Long appId) {
        appService.getApp(appId);
        return environmentRepository.findByAppIdOrderByCreatedAtDesc(appId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EnvironmentResponse updateEnvironment(Long environmentId, UpdateEnvironmentRequest request) {
        Environment environment = getEnvironment(environmentId);
        environment.update(
                request.name(),
                request.branchName(),
                request.baseUrl(),
                request.authType(),
                environmentSecretService.protect(request.authType(), request.authConfig()),
                request.headers(),
                request.defaultTestLevel(),
                defaultSource(request.defaultTestLevelSource())
        );
        return toResponse(environment);
    }

    @Transactional(readOnly = true)
    public ConnectionTestResponse testConnection(Long environmentId) {
        Environment environment = getEnvironment(environmentId);
        WebClient webClient = webClientBuilder.baseUrl(normalizeBaseUrl(environment.getBaseUrl())).build();
        Integer lastStatusCode = null;
        for (String path : CONNECTION_PROBE_PATHS) {
            try {
                int statusCode = webClient.get()
                        .uri(path)
                        .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode().value()))
                        .timeout(Duration.ofSeconds(3))
                        .block();
                lastStatusCode = statusCode;
                if (statusCode == 200) {
                    return new ConnectionTestResponse(
                            environment.getId(),
                            environment.getBranchName(),
                            "SUCCESS",
                            path,
                            statusCode,
                            path + "에서 200 응답을 확인했습니다."
                    );
                }
            } catch (Exception ignored) {
                lastStatusCode = null;
            }
        }

        return new ConnectionTestResponse(
                environment.getId(),
                environment.getBranchName(),
                "FAILED",
                null,
                lastStatusCode,
                "hello, up, actuator 후보 경로에서 200 응답을 확인하지 못했습니다."
        );
    }

    @Transactional(readOnly = true)
    public Environment getEnvironment(Long environmentId) {
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "실행 환경을 찾을 수 없습니다."));
    }

    private EnvironmentResponse toResponse(Environment environment) {
        LocalDateTime lastRunAt = executionRepository.findLatestEndedAtByEnvironmentId(environment.getId()).orElse(null);
        double coverage = averageCoverage(environment.getApp().getId());
        return EnvironmentResponse.from(environment, lastRunAt, coverage);
    }

    private double averageCoverage(Long appId) {
        List<ApiEndpoint> endpoints = apiEndpointRepository.findByAppId(appId);
        if (endpoints.isEmpty()) {
            return 0.0;
        }
        return endpoints.stream()
                .mapToDouble(endpoint -> coverageService.calculateCoveragePercent(endpoint.getId()))
                .average()
                .orElse(0.0);
    }

    private TestLevelSource defaultSource(TestLevelSource requestedSource) {
        return requestedSource == null ? TestLevelSource.MANUAL : requestedSource;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "http://" + trimmed;
    }
}
