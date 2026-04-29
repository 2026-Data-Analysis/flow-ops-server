package flowops.testcase.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import flowops.api.domain.entity.ApiEndpoint;
import flowops.api.service.ApiEndpointService;
import flowops.global.exception.ApiException;
import flowops.global.response.ErrorCode;
import flowops.testcase.domain.entity.TestCase;
import flowops.testcase.domain.entity.TestCaseVersion;
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
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<TestCaseSummaryResponse> listByApi(Long apiId) {
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
        return testCaseRepository.findByIdAndActiveTrue(testCaseId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "테스트케이스를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public TestCase getTestCase(Long testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "테스트케이스를 찾을 수 없습니다."));
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
