package flowops.aiintegration.controller;

import flowops.aiintegration.dto.request.OrchestratorDispatchRequest;
import flowops.aiintegration.dto.response.OrchestratorDispatchResponse;
import flowops.aiintegration.service.OrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Orchestrator", description = "멀티 에이전트 오케스트레이터 디스패치 API")
public class OrchestratorController {

    private final OrchestratorService orchestratorService;

    @PostMapping("/api/v1/orchestrator/dispatch")
    @Operation(
            summary = "오케스트레이터 디스패치",
            description = "context 필드를 기반으로 incident/testcase/scenario 에이전트 중 적절한 에이전트를 호출합니다."
    )
    public OrchestratorDispatchResponse dispatch(
            @Valid @RequestBody OrchestratorDispatchRequest request
    ) {
        return orchestratorService.dispatch(request);
    }
}
