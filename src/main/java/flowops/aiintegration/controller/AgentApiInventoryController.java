package flowops.aiintegration.controller;

import flowops.apiinventory.domain.entity.ApiHttpMethod;
import flowops.apiinventory.domain.entity.ApiInventorySource;
import flowops.apiinventory.dto.response.AgentApiInventorySearchResponse;
import flowops.apiinventory.service.ApiInventoryService;
import flowops.global.response.ApiResponse;
import flowops.global.swagger.CommonApiErrorResponses;
import flowops.testcase.domain.entity.TestLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CommonApiErrorResponses
@RestController
@RequestMapping("/ai/agents/api-inventories")
@RequiredArgsConstructor
@Tag(name = "AI 에이전트 API 인벤토리", description = "오케스트레이터와 에이전트 호출에 최적화된 API 인벤토리 조회 API")
public class AgentApiInventoryController {

    private final ApiInventoryService apiInventoryService;

    @GetMapping
    @Operation(
            summary = "에이전트용 API 인벤토리 조회",
            description = "자연어 요청을 처리하는 오케스트레이터가 테스트케이스 생성 에이전트 호출에 사용할 API 명세 목록을 조회합니다."
    )
    public ApiResponse<AgentApiInventorySearchResponse> searchInventories(
            @RequestParam @NotNull Long projectId,
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) Long repositoryId,
            @RequestParam(required = false) String branchName,
            @RequestParam(required = false) String domainTag,
            @RequestParam(required = false) ApiHttpMethod method,
            @RequestParam(required = false) TestLevel testLevel,
            @RequestParam(required = false) ApiInventorySource sourceType,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(apiInventoryService.searchInventoriesForAgent(
                projectId,
                appId,
                repositoryId,
                branchName,
                domainTag,
                method,
                testLevel,
                sourceType,
                keyword
        ));
    }
}
