package flowops.testcase.controller;

import flowops.global.response.ApiResponse;
import flowops.testcase.dto.request.UpdateTestCaseActiveRequest;
import flowops.testcase.dto.request.UpdateTestCaseRequest;
import flowops.testcase.dto.response.TestCaseDetailResponse;
import flowops.testcase.dto.response.TestCaseSummaryResponse;
import flowops.testcase.service.TestCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트케이스 조회, 수정, 삭제 API를 제공합니다.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Test Case", description = "테스트 케이스 조회 및 수정 API")
public class TestCaseController {

    private final TestCaseService testCaseService;

    @GetMapping("/apis/{apiId}/test-cases")
    @Operation(summary = "API별 테스트 케이스 목록 조회", description = "특정 API에 연결된 활성 테스트 케이스 목록을 조회합니다.")
    public ApiResponse<List<TestCaseSummaryResponse>> getTestCasesByApi(@PathVariable Long apiId) {
        return ApiResponse.success(testCaseService.listByApi(apiId));
    }

    @GetMapping("/test-cases/{testCaseId}")
    @Operation(summary = "테스트 케이스 상세 조회", description = "테스트 케이스 상세와 명세 정보를 조회합니다.")
    public ApiResponse<TestCaseDetailResponse> getTestCase(@PathVariable Long testCaseId) {
        return ApiResponse.success(testCaseService.getDetail(testCaseId));
    }

    @PatchMapping("/test-cases/{testCaseId}")
    @Operation(summary = "테스트 케이스 수정", description = "테스트 케이스를 수정하고 이전 버전 스냅샷을 저장합니다.")
    public ApiResponse<TestCaseDetailResponse> updateTestCase(
            @PathVariable Long testCaseId,
            @Valid @RequestBody UpdateTestCaseRequest request
    ) {
        return ApiResponse.success(testCaseService.update(testCaseId, request));
    }

    @PatchMapping("/test-cases/{testCaseId}/active")
    @Operation(summary = "테스트 케이스 활성 상태 변경", description = "테스트 케이스를 명시적으로 활성화하거나 비활성화합니다.")
    public ApiResponse<TestCaseDetailResponse> updateTestCaseActive(
            @PathVariable Long testCaseId,
            @Valid @RequestBody UpdateTestCaseActiveRequest request
    ) {
        return ApiResponse.success(testCaseService.updateActive(testCaseId, request));
    }

    @DeleteMapping("/test-cases/{testCaseId}")
    @Operation(summary = "테스트 케이스 비활성화", description = "테스트 케이스를 soft delete 방식으로 비활성화합니다.")
    public ApiResponse<Void> deleteTestCase(@PathVariable Long testCaseId) {
        testCaseService.deactivate(testCaseId);
        return ApiResponse.success(null);
    }
}
