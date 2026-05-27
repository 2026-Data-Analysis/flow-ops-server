package flowops.aiintegration.controller;

import flowops.aiintegration.dto.request.AgentTestCaseGenerateRequest;
import flowops.aiintegration.service.AgentTestCaseGenerateService;
import flowops.aiintegration.service.AgentTestCaseGenerateService.AgentBadRequestException;
import flowops.integration.ai.AiAgentContracts.TestCaseGeneratorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "테스트케이스 생성 에이전트", description = "AI 테스트케이스 생성 에이전트 직접 호출 API")
public class TestCaseAgentController {

    private final AgentTestCaseGenerateService agentTestCaseGenerateService;

    @PostMapping("/api/v1/agents/testcase/generate")
    @Operation(
            summary = "테스트케이스 초안 생성",
            description = "AI 에이전트를 통해 API 명세 기반 테스트케이스 초안을 생성합니다. "
                    + "apis가 비어 있으면 400, FROM_FAILURE 모드는 미지원(400), "
                    + "LLM 2회 재시도 후 생성 실패 시 400을 반환합니다."
    )
    public ResponseEntity<TestCaseGeneratorResponse> generate(
            @RequestBody AgentTestCaseGenerateRequest request
    ) {
        return ResponseEntity.ok(agentTestCaseGenerateService.generate(request));
    }

    @ExceptionHandler(AgentBadRequestException.class)
    public ResponseEntity<AgentErrorDetail> handleBadRequest(AgentBadRequestException exception) {
        return ResponseEntity.badRequest().body(new AgentErrorDetail(exception.getMessage()));
    }

    private record AgentErrorDetail(String detail) {
    }
}
