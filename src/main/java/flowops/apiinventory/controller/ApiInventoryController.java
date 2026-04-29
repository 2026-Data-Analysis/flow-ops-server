package flowops.apiinventory.controller;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.dto.request.SaveApiInventoryRequest;
import flowops.apiinventory.dto.response.ApiInventoryDetailResponse;
import flowops.apiinventory.dto.response.ApiInventoryListResponse;
import flowops.apiinventory.dto.response.ApiInventoryResponse;
import flowops.apiinventory.service.ApiInventoryService;
import flowops.global.response.ApiResponse;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API Inventory의 조회와 수동 저장 API를 제공합니다.
 */
@RestController
@RequestMapping("/projects/{projectId}/api-inventories")
@RequiredArgsConstructor
@Tag(name = "Legacy API Inventory", description = "기존 API 인벤토리 저장 API")
public class ApiInventoryController {

    private final ApiInventoryService apiInventoryService;

    @GetMapping
    @Operation(summary = "API 인벤토리 조회", description = "프로젝트 API 인벤토리를 저장소와 브랜치 기준으로 조회합니다.")
    public ApiResponse<ApiInventoryListResponse> listInventories(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) String domainTag,
            @RequestParam(required = false) ApiHttpMethod method,
            @RequestParam(required = false) TestLevel testLevel,
            @RequestParam(required = false) ApiInventorySource sourceType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(apiInventoryService.listInventories(
                projectId,
                repositoryId,
                branchName,
                domainTag,
                method,
                testLevel,
                sourceType,
                keyword
        ));
    }

    @GetMapping("/{inventoryId}")
    @Operation(summary = "API 인벤토리 상세 조회", description = "API 인벤토리의 테스트, 커버리지, 성공률 정보를 함께 조회합니다.")
    public ApiResponse<ApiInventoryDetailResponse> getInventoryDetail(
            @PathVariable Long projectId,
            @PathVariable Long inventoryId
    ) {
        return ApiResponse.success(apiInventoryService.getInventoryDetail(projectId, inventoryId));
    }

    @PostMapping
    @Operation(summary = "API 인벤토리 저장", description = "기존 프로젝트 기준 API 인벤토리 항목을 저장합니다.")
    public ApiResponse<ApiInventoryResponse> saveInventory(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveApiInventoryRequest request
    ) {
        return ApiResponse.success(apiInventoryService.saveInventory(projectId, request));
    }
}
