package flowops.testcase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.domain.entity.ApiMethod;
import flowops.api.repository.ApiEndpointRepository;
import flowops.api.service.ApiEndpointService;
import flowops.apiinventory.domain.entity.ApiInventory;
import flowops.apiinventory.repository.ApiInventoryRepository;
import flowops.app.domain.entity.App;
import flowops.app.repository.AppRepository;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseSource;
import flowops.testcase.domain.entity.TestCaseType;
import flowops.testcase.domain.entity.TestCaseVersion;
import flowops.testcase.domain.entity.TestLevel;
import flowops.testcase.dto.request.CreateTestCaseRequest;
import flowops.testcase.dto.request.UpdateTestCaseActiveRequest;
import flowops.testcase.dto.request.UpdateTestCaseRequest;
import flowops.testcase.dto.response.TestCaseDetailResponse;
import flowops.testcase.dto.response.TestCaseSummaryResponse;
import flowops.testcase.repository.TestCaseRepository;
import flowops.testcase.repository.TestCaseVersionRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트케이스 조회, 수정, 비활성화와 버전 스냅샷 저장을 담당합니다.
 */
@Service
@RequiredArgsConstructor
public class TestCaseService {

    private final TestCaseRepository testCaseRepository;
    private final TestCaseVersionRepository testCaseVersionRepository;
    private final ApiEndpointService apiEndpointService;
    private final ApiEndpointRepository apiEndpointRepository;
    private final ApiInventoryRepository apiInventoryRepository;
    private final AppRepository appRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public TestCaseDetailResponse create(Long appId, CreateTestCaseRequest request) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "앱을 찾을 수 없습니다."));

        ApiInventory apiInventory = request.apiInventoryId() != null
                ? apiInventoryRepository.findById(request.apiInventoryId()).orElse(null)
                : null;

        ApiEndpoint apiEndpoint = resolveApiEndpoint(appId, request, apiInventory);

        TestCase testCase = TestCase.builder()
                .app(app)
                .apiEndpoint(apiEndpoint)
                .apiInventory(apiInventory)
                .name(request.name())
                .description(request.description())
                .type(request.type() != null ? request.type() : TestCaseType.HAPPY_PATH)
                .testLevel(request.testLevel() != null ? request.testLevel() : TestLevel.REGRESSION)
                .source(TestCaseSource.AUTO)
                .userRole(request.userRole())
                .stateCondition(request.stateCondition())
                .dataVariant(request.dataVariant())
                .requestSpec(request.requestSpec())
                .expectedSpec(request.expectedSpec())
                .assertionSpec(request.assertionSpec())
                .active(true)
                .version(1)
                .build();

        return TestCaseDetailResponse.from(testCaseRepository.save(testCase));
    }

    private ApiEndpoint resolveApiEndpoint(Long appId, CreateTestCaseRequest request, ApiInventory apiInventory) {
        if (request.apiEndpointId() != null) {
            return apiEndpointRepository.findById(request.apiEndpointId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API 엔드포인트를 찾을 수 없습니다."));
        }
        if (apiInventory != null) {
            ApiMethod method = ApiMethod.valueOf(apiInventory.getMethod().name());
            return apiEndpointRepository.findFirstByAppIdAndMethodAndPath(appId, method, apiInventory.getEndpointPath())
                    .or(() -> apiEndpointRepository.findFirstByMethodAndPath(method, apiInventory.getEndpointPath()))
                    .orElseGet(() -> apiEndpointRepository.findByAppId(appId).stream().findFirst()
                            .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 앱에 연결된 API 엔드포인트가 없습니다.")));
        }
        return apiEndpointRepository.findByAppId(appId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "해당 앱에 연결된 API 엔드포인트가 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<TestCaseSummaryResponse> listByApp(Long appId, String domainTag, String method) {
        return testCaseRepository.findByAppIdAndActiveTrueOrderByUpdatedAtDesc(appId)
                .stream()
                .filter(tc -> matchesDomainTag(tc, domainTag))
                .filter(tc -> matchesMethod(tc, method))
                .map(tc -> TestCaseSummaryResponse.from(tc, tc.getApiEndpoint()))
                .toList();
    }

    private boolean matchesDomainTag(TestCase tc, String domainTag) {
        if (domainTag == null || domainTag.isBlank()) return true;
        return domainTag.equals(tc.getApiEndpoint().getDomainTag());
    }

    private boolean matchesMethod(TestCase tc, String method) {
        if (method == null || method.isBlank()) return true;
        return method.equals(tc.getApiEndpoint().getMethod().name());
    }

    @Transactional(readOnly = true)
    public List<TestCaseSummaryResponse> listByApi(Long apiId) {
        return apiInventoryRepository.findById(apiId)
                .map(this::listByInventory)
                .orElseGet(() -> listByEndpoint(apiId));
    }

    private List<TestCaseSummaryResponse> listByInventory(ApiInventory apiInventory) {
        List<TestCase> testCases = apiInventory.getRepositoryInfo() != null
                ? testCaseRepository.findByRepositoryAndMethodAndPathAndActiveTrueOrderByUpdatedAtDesc(
                        apiInventory.getRepositoryInfo().getId(),
                        apiInventory.getMethod(),
                        apiInventory.getEndpointPath()
                )
                : testCaseRepository.findByApiInventoryIdAndActiveTrueOrderByUpdatedAtDesc(apiInventory.getId());
        if (testCases.isEmpty()) {
            return List.of();
        }
        ApiEndpoint selectedEndpoint = testCases.get(0).getApiEndpoint();
        return testCases.stream()
                .map(testCase -> TestCaseSummaryResponse.from(testCase, selectedEndpoint))
                .toList();
    }

    private List<TestCaseSummaryResponse> listByEndpoint(Long apiId) {
        ApiEndpoint selectedEndpoint = apiEndpointService.getApiEndpoint(apiId);
        return testCaseRepository.findByApiEndpointIdAndActiveTrueOrderByUpdatedAtDesc(apiId)
                .stream()
                .map(testCase -> TestCaseSummaryResponse.from(testCase, selectedEndpoint))
                .toList();
    }

    @Transactional(readOnly = true)
    public TestCaseDetailResponse getDetail(Long testCaseId) {
        return TestCaseDetailResponse.from(getActiveTestCase(testCaseId));
    }

    @Transactional
    public TestCaseDetailResponse update(Long testCaseId, UpdateTestCaseRequest request) {
        TestCase testCase = getActiveTestCase(testCaseId);
        saveVersionSnapshot(testCase);
        testCase.update(
                request.name(),
                request.description(),
                request.type(),
                request.testLevel(),
                request.userRole(),
                request.stateCondition(),
                request.dataVariant(),
                request.requestSpec(),
                request.expectedSpec(),
                request.assertionSpec()
        );
        return TestCaseDetailResponse.from(testCase);
    }

    @Transactional
    public void deactivate(Long testCaseId) {
        TestCase testCase = getActiveTestCase(testCaseId);
        saveVersionSnapshot(testCase);
        testCase.deactivate();
    }

    @Transactional
    public TestCaseDetailResponse updateActive(Long testCaseId, UpdateTestCaseActiveRequest request) {
        TestCase testCase = getTestCase(testCaseId);
        if (testCase.isActive() != request.active()) {
            saveVersionSnapshot(testCase);
            testCase.changeActive(request.active());
        }
        return TestCaseDetailResponse.from(testCase);
    }

    @Transactional(readOnly = true)
    public TestCase getActiveTestCase(Long testCaseId) {
        validatePositiveId(testCaseId);
        return testCaseRepository.findByIdAndActiveTrue(testCaseId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "테스트케이스를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public TestCase getTestCase(Long testCaseId) {
        validatePositiveId(testCaseId);
        return testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "테스트케이스를 찾을 수 없습니다."));
    }

    // 0 또는 음수 ID는 유효하지 않은 요청(400)으로 처리한다.
    private void validatePositiveId(Long testCaseId) {
        if (testCaseId == null || testCaseId <= 0) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "테스트케이스 ID는 1 이상의 값이어야 합니다.");
        }
    }

    private void saveVersionSnapshot(TestCase testCase) {
        try {
            String snapshotJson = objectMapper.writeValueAsString(TestCaseDetailResponse.from(testCase));
            testCaseVersionRepository.save(TestCaseVersion.builder()
                    .testCase(testCase)
                    .version(testCase.getVersion())
                    .snapshotJson(snapshotJson)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR, "테스트케이스 버전 스냅샷 생성에 실패했습니다.");
        }
    }
}
