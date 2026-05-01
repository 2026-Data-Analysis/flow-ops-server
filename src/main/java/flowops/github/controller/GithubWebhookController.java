package flowops.github.controller;

import flowops.github.dto.response.GithubWebhookResult;
import flowops.github.service.GithubWebhookService;
import flowops.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github/webhooks")
@RequiredArgsConstructor
@Tag(name = "GitHub Webhook", description = "GitHub webhook 기반 자동 트리거 API")
public class GithubWebhookController {

    private final GithubWebhookService githubWebhookService;

    @PostMapping
    @Operation(
            summary = "GitHub webhook 수신",
            description = "Pull Request가 dev, staging, main 등 설정된 브랜치로 머지되면 해당 브랜치의 API Inventory를 갱신합니다."
    )
    public ApiResponse<GithubWebhookResult> handleWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload
    ) {
        return ApiResponse.success(githubWebhookService.handle(event, signature, payload));
    }
}
